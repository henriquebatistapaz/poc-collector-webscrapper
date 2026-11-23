package dev.henrique.collectorwebscrapper;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class Identity {
  private Identity() {}
  public static String fingerprint(String source, String title, String publishedAt, String author, String url) {
    List<String> values = List.of(source, normalize(title), publishedAt == null ? "" : publishedAt,
        normalize(author), canonicalUrl(url));
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.join("\0", values).getBytes(StandardCharsets.UTF_8));
      return "fingerprint:v1:" + HexFormat.of().formatHex(digest);
    } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
  }
  static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
  }
  static String canonicalUrl(String value) {
    URI u = UrlSafety.validateOutputUrl(value);
    List<String> kept = new ArrayList<>();
    if (u.getRawQuery() != null) for (String part : u.getRawQuery().split("&")) {
      String key = part.split("=", 2)[0];
      String decoded = URLDecoder.decode(key, StandardCharsets.UTF_8).toLowerCase();
      if (!decoded.startsWith("utm_") && !List.of("fbclid", "gclid", "mc_cid", "mc_eid").contains(decoded)) kept.add(part);
    }
    String query = kept.isEmpty() ? "" : "?" + String.join("&", kept);
    return u.getScheme().toLowerCase() + "://" + u.getRawAuthority().toLowerCase() + u.getRawPath() + query;
  }
}
