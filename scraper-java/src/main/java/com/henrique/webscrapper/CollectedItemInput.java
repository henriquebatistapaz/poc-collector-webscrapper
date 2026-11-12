package com.henrique.webscrapper;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"source", "externalId", "title", "link", "publishedAt"})
public record CollectedItemInput(String source, String externalId, String title, String link, String publishedAt) {
  public CollectedItemInput {
    if (source == null || source.isBlank() || source.length() > 40) throw new IllegalArgumentException("Invalid source");
    if (externalId == null || externalId.isBlank() || externalId.length() > 2000) throw new IllegalArgumentException("Invalid externalId");
    if (title == null || title.isBlank() || title.length() > 1000) throw new IllegalArgumentException("Invalid title");
    UrlSafety.validateOutputUrl(link);
    if (publishedAt != null) java.time.Instant.parse(publishedAt);
  }
}
