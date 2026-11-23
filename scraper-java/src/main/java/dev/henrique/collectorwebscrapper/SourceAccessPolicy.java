package dev.henrique.collectorwebscrapper;

import java.util.ArrayList;
import java.util.List;

public final class SourceAccessPolicy {
  public static final int MAX_RESPONSE_BYTES = 2_000_000;
  private SourceAccessPolicy() {}

  public static boolean liveCollectionEnabled(String source) { return false; }

  public static List<String> detailPages(List<String> candidates, int configuredLimit) {
    int limit = Math.max(0, Math.min(5, configuredLimit));
    return new ArrayList<>(candidates.subList(0, Math.min(limit, candidates.size())));
  }

  public static void validateResponseSize(long size) {
    if (size < 0 || size > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Response exceeds size limit");
  }

  public static boolean sameOriginRedirect(String source, String original, String destination) {
    try {
      var first = UrlSafety.validateSourcePageUrl(source, original);
      var next = UrlSafety.validateSourcePageUrl(source, destination);
      return first.getHost().equalsIgnoreCase(next.getHost());
    } catch (IllegalArgumentException e) { return false; }
  }

  public static FetchStatus classify(int statusCode, boolean timedOut, boolean markupValid) {
    if (timedOut) return FetchStatus.TIMEOUT;
    if (statusCode == 401 || statusCode == 403) return FetchStatus.DENIED;
    if (statusCode == 429) return FetchStatus.THROTTLED;
    if (statusCode >= 500) return FetchStatus.UPSTREAM_ERROR;
    if (statusCode < 200 || statusCode >= 300 || !markupValid) return FetchStatus.INVALID_RESPONSE;
    return FetchStatus.OK;
  }

  public enum FetchStatus { OK, DENIED, THROTTLED, TIMEOUT, UPSTREAM_ERROR, INVALID_RESPONSE }
}
