package dev.henrique.webscrapper;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class HostRequestGate {
  private final ConcurrentHashMap<String, Semaphore> gates = new ConcurrentHashMap<>();
  public Permit acquire(String url) throws InterruptedException {
    String host = URI.create(url).getHost().toLowerCase();
    Semaphore semaphore = gates.computeIfAbsent(host, ignored -> new Semaphore(1, true));
    semaphore.acquire();
    return semaphore::release;
  }
  @FunctionalInterface public interface Permit extends AutoCloseable { @Override void close(); }
}
