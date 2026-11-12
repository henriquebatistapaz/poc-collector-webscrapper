package com.henrique.webscrapper;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

class SourceParserTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void parsesHackerNewsItemAndVisibleCommentsWithoutFollowingLinks() throws Exception {
    var html = Files.readString(Path.of("../contracts/fixtures/hacker-news-item.html"));
    var envelope = SourceParser.parse("hacker-news", "https://news.ycombinator.com/item?id=123456", html);
    assertEquals("123456", envelope.item().externalId());
    assertEquals("https://news.example/story/1", envelope.item().link());
    assertEquals(2, envelope.pageContent().comments().size());
    assertTrue(envelope.pageContent().mainText().contains("Example story"));
    assertEquals(0, envelope.entities().size());
    JsonNode json = mapper.valueToTree(envelope);
    assertFalse(json.toString().contains("Visible comment one"));
    assertFalse(json.get("item").has("pageContent"));
  }

  @Test
  void parsesWordPressNewsPage() throws Exception {
    var html = Files.readString(Path.of("../contracts/fixtures/wordpress-news-item.html"));
    var envelope = SourceParser.parse("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/", html);
    assertEquals("21967", envelope.item().externalId());
    assertEquals("https://wordpress.org/news/2026/11/exemplo/", envelope.item().link());
    assertEquals(1, envelope.pageContent().comments().size());
    assertEquals(0, envelope.entities().size());
  }

  @Test
  void usesVersionedFingerprintWhenWordPressNativeIdIsMissing() throws Exception {
    var html = Files.readString(Path.of("../contracts/fixtures/wordpress-news-item.html"))
        .replace("post-21967", "post");
    var envelope = SourceParser.parse("wordpress-news",
        "https://wordpress.org/news/2026/11/exemplo/?utm_source=test#section", html);
    assertEquals("fingerprint:v1:0307fdbf8a9eef3e918e0c588c1702bd42fe4424e43e57b8faaa174c8b3e8374",
        envelope.item().externalId());
  }

  @Test
  void rejectsInvalidAndPrivateSourceUrlsAndNeverFetchesExternalItemLinks() {
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("javascript:alert(1)"));
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("file:///etc/passwd"));
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("/news/relative"));
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("https://user:pass@example.com/"));
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("https://example.com/" + "x".repeat(2000)));
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateResolvedAddress("127.0.0.1"));
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateResolvedAddress("10.0.0.1"));
    assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateResolvedAddress("mixed.example",
        host -> new InetAddress[] {InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.1")}));
  }

  @Test
  void createsStableFingerprintAndDeduplicatesBySourceAndId() throws Exception {
    assertEquals("fingerprint:v1:0307fdbf8a9eef3e918e0c588c1702bd42fe4424e43e57b8faaa174c8b3e8374",
        Identity.fingerprint("wordpress-news", "Olá Mundo", "2026-11-12T12:30:00Z", "Equipe News", "https://wordpress.org/news/2026/11/exemplo/?utm_source=x#top"));
    var registry = new ItemRegistry();
    assertTrue(registry.add("a", "1"));
    assertFalse(registry.add("a", "1"));
    assertTrue(registry.add("b", "1"));
  }

  @Test
  void deduplicatesTheEmittedBatchBySourceAndExternalId() throws Exception {
    var wordpressHtml = Files.readString(Path.of("../contracts/fixtures/wordpress-news-item.html"));
    var hackerNewsHtml = Files.readString(Path.of("../contracts/fixtures/hacker-news-item.html"))
        .replace("123456", "21967");
    var first = SourceParser.parse("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/", wordpressHtml);
    var duplicate = SourceParser.parse("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/", wordpressHtml);
    var otherSource = SourceParser.parse("hacker-news", "https://news.ycombinator.com/item?id=21967", hackerNewsHtml);

    var output = EnvelopeBatch.deduplicate(List.of(first, duplicate, otherSource));

    assertEquals(2, output.size());
    assertEquals(List.of("wordpress-news", "hacker-news"),
        output.stream().map(envelope -> envelope.item().source()).toList());
  }

  @Test
  void javaCliEmitsDuplicateSourceItemOnlyOnce() throws Exception {
    var captured = new ByteArrayOutputStream();
    var original = System.out;
    try (var redirected = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setOut(redirected);
      Main.main(new String[] {"parse", "wordpress-news",
          "https://wordpress.org/news/2026/11/exemplo/", "../contracts/fixtures/wordpress-news-item.html",
          "https://wordpress.org/news/2026/11/exemplo/", "../contracts/fixtures/wordpress-news-item.html"});
    } finally { System.setOut(original); }
    assertEquals(1, captured.toString(StandardCharsets.UTF_8).lines().count());
  }

  @Test
  void validatesEnvelopeAgainstSharedSchema() throws Exception {
    var html = Files.readString(Path.of("../contracts/fixtures/wordpress-news-item.html"));
    var envelope = SourceParser.parse("wordpress-news", "https://wordpress.org/news/2026/11/exemplo/", html);
    var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(Path.of("../contracts/envelope-v1.schema.json").toUri());
    var errors = schema.validate(mapper.valueToTree(envelope));
    assertTrue(errors.isEmpty(), errors.toString());
  }

  @Test
  void keepsLiveSourcesDisabledAndHonorsRobotsAndRequestBudgets() {
    assertFalse(SourceAccessPolicy.liveCollectionEnabled("hacker-news"));
    var rules = RobotsRules.parse("User-agent: *\nCrawl-delay: 30\nDisallow: /vote?\nDisallow: /login\n");
    assertEquals(30, rules.crawlDelaySeconds());
    assertFalse(rules.allows("/vote?id=1"));
    assertTrue(rules.allows("/item?id=1"));
    assertEquals(5, SourceAccessPolicy.detailPages(List.of("1", "2", "3", "4", "5", "6"), 10).size());
    assertEquals(SourceAccessPolicy.FetchStatus.DENIED, SourceAccessPolicy.classify(403, false, true));
    assertEquals(SourceAccessPolicy.FetchStatus.THROTTLED, SourceAccessPolicy.classify(429, false, true));
    assertEquals(SourceAccessPolicy.FetchStatus.TIMEOUT, SourceAccessPolicy.classify(200, true, true));
    assertEquals(SourceAccessPolicy.FetchStatus.DENIED, SourceAccessPolicy.classify(401, false, true));
    assertEquals(SourceAccessPolicy.FetchStatus.UPSTREAM_ERROR, SourceAccessPolicy.classify(503, false, true));
    assertEquals(SourceAccessPolicy.FetchStatus.INVALID_RESPONSE, SourceAccessPolicy.classify(200, false, false));
    assertThrows(IllegalArgumentException.class, () -> SourceAccessPolicy.validateResponseSize(2_000_001));
  }

  @Test
  void catalogKeepsUnreviewedSourcesDisabled() throws Exception {
    JsonNode catalog = mapper.readTree(Path.of("../contracts/source-catalog.json").toFile());
    for (JsonNode source : catalog.get("sources")) {
      assertTrue(source.get("selectedForMvp").asBoolean());
      assertFalse(source.get("liveEnabled").asBoolean());
      assertEquals(5, source.get("maxDetailPagesPerRun").asInt());
      assertEquals(1, source.get("maxConcurrentPerHost").asInt());
    }
  }

  @Test
  void rejectsRedirectsOutsideSourceAllowlist() {
    assertFalse(SourceAccessPolicy.sameOriginRedirect("hacker-news",
        "https://news.ycombinator.com/item?id=1", "https://evil.example/item?id=1"));
  }

  @Test
  void limitsConcurrentRequestsPerHostToOne() throws Exception {
    var gate = new HostRequestGate();
    var active = new AtomicInteger();
    var maximum = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(6)) {
      var jobs = java.util.stream.IntStream.range(0, 6).<Runnable>mapToObj(i -> () -> {
        try (var permit = gate.acquire("https://news.ycombinator.com/item?id=" + i)) {
          int current = active.incrementAndGet();
          maximum.accumulateAndGet(current, Math::max);
          Thread.sleep(20);
          active.decrementAndGet();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
      }).toList();
      for (var job : jobs) pool.submit(job);
      pool.shutdown();
      assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
    }
    assertEquals(1, maximum.get());
  }
}
