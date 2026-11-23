package dev.henrique.collectorwebscrapper;

import java.util.List;

public record PageContent(String mainText, List<String> comments) {
  public PageContent {
    comments = List.copyOf(comments);
  }
}
