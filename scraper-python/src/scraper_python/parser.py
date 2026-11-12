from datetime import datetime, timezone

from scrapy import Selector

from .identity import fingerprint
from .models import CollectedEnvelope, CollectedItemInput, PageContent
from .safety import validate_output_url, validate_source_page_url
from .policy import MAX_RESPONSE_BYTES, validate_response_size


def _date(value: str | None) -> datetime | None:
    if not value:
        return None
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return parsed.astimezone(timezone.utc)


def parse_page(source: str, page_url: str, html: str) -> CollectedEnvelope:
    validate_response_size(len(html.encode("utf-8")))
    validate_source_page_url(source, page_url)
    selector = Selector(text=html, type="html")
    if source == "hacker-news":
        row = selector.css("tr.athing.submission[id]") or selector.css("tr.athing[id]")
        if not row:
            raise ValueError("Hacker News story markup not found")
        row = row[0]
        title_values = row.css("span.titleline > a::text").getall()
        href = row.css("span.titleline > a::attr(href)").get()
        title = " ".join(" ".join(title_values).split())
        if not title or not href:
            raise ValueError("Hacker News story title/link missing")
        external_id = row.attrib.get("id", "")
        date_value = selector.css("span.age::attr(title)").get()
        author = selector.css("a.hnuser::text").get(default="")
        if not external_id.isdigit():
            parsed_date = _date(date_value)
            published_utc = parsed_date.isoformat().replace("+00:00", "Z") if parsed_date else None
            external_id = fingerprint(source, title, published_utc, author, page_url)
        comments = [" ".join(" ".join(comment.xpath(".//text()").getall()).split())
                    for comment in selector.css(".comment-tree .commtext")]
        comments = [comment for comment in comments if comment]
        main_text = title
        link = href
    elif source == "wordpress-news":
        article = selector.css("article.post") or selector.css("article")
        if not article:
            raise ValueError("WordPress News article markup not found")
        article = article[0]
        title = " ".join(article.css(".entry-title::text").getall()).strip()
        content_nodes = article.css(".entry-content")
        if not title or not content_nodes:
            raise ValueError("WordPress News title/content missing")
        content = content_nodes[0]
        main_text = " ".join(" ".join(content.xpath(".//text()[not(ancestor::script)]").getall()).split())
        date_value = article.css("time[datetime]::attr(datetime)").get()
        published_at = _date(date_value)
        author = " ".join(article.css("a[rel=author]::text").getall()).strip()
        comments = [" ".join(" ".join(c.xpath(".//text()").getall()).split())
                    for c in article.css(".comment-content")]
        comments = [comment for comment in comments if comment]
        native_id = next((token[5:] for token in article.attrib.get("class", "").split()
                          if token.startswith("post-") and token[5:].isdigit()), "")
        published_utc = published_at.isoformat().replace("+00:00", "Z") if published_at else None
        external_id = native_id or fingerprint(source, title, published_utc, author, page_url)
        link = page_url
    else:
        raise ValueError(f"Unknown source: {source}")
    link = validate_output_url(link)
    if source == "hacker-news":
        published_at = _date(date_value)
    item = CollectedItemInput(source=source, externalId=external_id, title=title, link=link,
                              publishedAt=published_at)
    envelope = CollectedEnvelope(schemaVersion="1.0", collectedAt=datetime.now(timezone.utc),
                                 sourcePageUrl=page_url, item=item, entities=[])
    envelope.set_page_content(PageContent(main_text=main_text, comments=comments))
    return envelope
