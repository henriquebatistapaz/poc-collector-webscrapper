package dev.henrique.webscrapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public final class SourceParser {
  private SourceParser() {}
  public static CollectedEnvelope parse(String source, String pageUrl, String html) {
    if (html == null || html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SourceAccessPolicy.MAX_RESPONSE_BYTES)
      throw new IllegalArgumentException("HTML payload missing or too large");
    UrlSafety.validateSourcePageUrl(source, pageUrl);
    Document doc = Jsoup.parse(html, pageUrl);
    String title;
    String externalId;
    String link;
    String publishedAt = null;
    String author = "";
    String mainText;
    List<String> comments = new ArrayList<>();
    if (source.equals("hacker-news")) {
      Element row = doc.selectFirst("tr.athing.submission[id]");
      if (row == null) row = doc.selectFirst("tr.athing[id]");
      Element titleLink = row == null ? null : row.selectFirst("span.titleline > a");
      if (titleLink == null) throw new IllegalArgumentException("Hacker News story markup not found");
      externalId = row.id();
      if (!externalId.matches("[0-9]+")) externalId = "";
      title = titleLink.text().trim();
      link = titleLink.absUrl("href");
      if (link.isBlank()) link = pageUrl;
      Element age = doc.selectFirst("span.age[title]");
      if (age != null) publishedAt = parseDate(age.attr("title"));
      Element user = doc.selectFirst("a.hnuser");
      if (user != null) author = user.text();
      if (externalId.isBlank()) externalId = Identity.fingerprint(source, title, publishedAt, author, pageUrl);
      mainText = title;
      for (Element comment : doc.select(".comment-tree .commtext")) if (!comment.text().isBlank()) comments.add(comment.text());
    } else if (source.equals("wordpress-news")) {
      Element article = doc.selectFirst("article.post");
      if (article == null) article = doc.selectFirst("article");
      Element titleElement = article == null ? null : article.selectFirst(".entry-title");
      Element content = article == null ? null : article.selectFirst(".entry-content");
      if (titleElement == null || content == null) throw new IllegalArgumentException("WordPress News markup not found");
      title = titleElement.text().trim();
      externalId = article.classNames().stream().filter(c -> c.matches("post-[0-9]+")).map(c -> c.substring(5)).findFirst().orElse("");
      if (!externalId.matches("[0-9]+")) externalId = "";
      Element date = article.selectFirst("time[datetime]");
      if (date != null) publishedAt = parseDate(date.attr("datetime"));
      if (externalId.isBlank()) externalId = Identity.fingerprint(source, title,
          publishedAt == null ? "" : publishedAt,
          article.select("a[rel=author]").text(), pageUrl);
      link = pageUrl;
      author = article.select("a[rel=author]").text();
      mainText = content.text();
      for (Element comment : article.select(".comment-content")) if (!comment.text().isBlank()) comments.add(comment.text());
    } else throw new IllegalArgumentException("Unknown source: " + source);
    link = UrlSafety.validateOutputUrl(link).toString();
    if (title.isBlank()) throw new IllegalArgumentException("Item title is empty");
    var item = new CollectedItemInput(source, externalId, title, link, publishedAt);
    return new CollectedEnvelope("1.0", Instant.now().toString(), pageUrl, item, List.of(), new PageContent(mainText, comments));
  }
  private static String parseDate(String value) {
    try { return Instant.parse(value).toString(); }
    catch (Exception e) { throw new IllegalArgumentException("Invalid published date", e); }
  }
}
