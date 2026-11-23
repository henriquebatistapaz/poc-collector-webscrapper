package dev.henrique.webscrapper;

import java.util.ArrayList;
import java.util.List;

public final class RobotsRules {
  private final List<String> disallowed = new ArrayList<>();
  private int crawlDelaySeconds;

  public static RobotsRules parse(String robotsTxt) {
    RobotsRules result = new RobotsRules();
    boolean applies = false;
    for (String raw : robotsTxt.split("\\R")) {
      String[] pair = raw.split(":", 2);
      if (pair.length != 2) continue;
      String key = pair[0].trim().toLowerCase();
      String value = pair[1].trim();
      if (key.equals("user-agent")) applies = value.equals("*");
      else if (applies && key.equals("disallow") && !value.isBlank()) result.disallowed.add(value);
      else if (applies && key.equals("crawl-delay")) {
        try { result.crawlDelaySeconds = (int) Math.ceil(Double.parseDouble(value)); }
        catch (NumberFormatException ignored) { result.crawlDelaySeconds = 0; }
      }
    }
    return result;
  }

  public boolean allows(String path) { return disallowed.stream().noneMatch(path::startsWith); }
  public int crawlDelaySeconds() { return crawlDelaySeconds; }
}
