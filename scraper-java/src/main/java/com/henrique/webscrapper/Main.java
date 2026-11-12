package com.henrique.webscrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Main {
  private Main() {}
  public static void main(String[] args) throws Exception {
    if (args.length < 4 || (args.length - 2) % 2 != 0 || !args[0].equals("parse")) {
      System.err.println("Usage: mvn exec:java -Dexec.mainClass=com.henrique.webscrapper.Main -Dexec.args='parse <source> <source-page-url> <html-file> [<source-page-url> <html-file> ...]'");
      System.exit(2);
    }
    List<CollectedEnvelope> parsed = new ArrayList<>();
    for (int i = 2; i < args.length; i += 2) {
      parsed.add(SourceParser.parse(args[1], args[i], Files.readString(Path.of(args[i + 1]))));
    }
    var mapper = new ObjectMapper();
    for (var envelope : EnvelopeBatch.deduplicate(parsed)) {
      System.out.println(mapper.writeValueAsString(envelope));
    }
  }
}
