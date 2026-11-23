package dev.henrique.webscrapper;

import java.util.ArrayList;
import java.util.List;

public final class EnvelopeBatch {
  private EnvelopeBatch() {}

  public static List<CollectedEnvelope> deduplicate(List<CollectedEnvelope> envelopes) {
    var registry = new ItemRegistry();
    var unique = new ArrayList<CollectedEnvelope>();
    for (var envelope : envelopes) {
      if (registry.add(envelope.item().source(), envelope.item().externalId())) unique.add(envelope);
    }
    return List.copyOf(unique);
  }
}
