package com.henrique.webscrapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonPropertyOrder({"schemaVersion", "collectedAt", "sourcePageUrl", "item", "entities"})
public record CollectedEnvelope(String schemaVersion, String collectedAt, String sourcePageUrl,
                                CollectedItemInput item, List<Object> entities,
                                @JsonIgnore PageContent pageContent) {
  public CollectedEnvelope {
    entities = List.copyOf(entities);
  }
}
