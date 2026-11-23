package dev.henrique.collectorwebscrapper;

import java.util.HashSet;
import java.util.Set;

public final class ItemRegistry {
  private final Set<String> keys = new HashSet<>();
  public boolean add(String source, String externalId) { return keys.add(source + "\0" + externalId); }
}
