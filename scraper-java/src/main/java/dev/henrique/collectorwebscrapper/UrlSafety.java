package dev.henrique.collectorwebscrapper;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;

public final class UrlSafety {
  private UrlSafety() {}

  public static URI validateOutputUrl(String value) {
    if (value == null || value.length() > 2000) throw new IllegalArgumentException("URL missing or too long");
    URI uri;
    try { uri = URI.create(value); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid URL", e); }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
        || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("Unsafe URL");
    return uri;
  }

  public static URI validateSourcePageUrl(String source, String value) {
    URI uri = validateOutputUrl(value);
    if (!uri.getScheme().equalsIgnoreCase("https") || (uri.getPort() != -1 && uri.getPort() != 443))
      throw new IllegalArgumentException("Source page must use HTTPS on the standard port");
    String host = uri.getHost().toLowerCase();
    boolean allowed = switch (source) {
      case "hacker-news" -> host.equals("news.ycombinator.com") && uri.getPath().equals("/item");
      case "wordpress-news" -> host.equals("wordpress.org") && uri.getPath().startsWith("/news/");
      default -> false;
    };
    if (!allowed) throw new IllegalArgumentException("Source page is not allowlisted");
    return uri;
  }

  public static void validateResolvedAddress(String address) {
    validateResolvedAddress(address, InetAddress::getAllByName);
  }

  static void validateResolvedAddress(String host, AddressResolver resolver) {
    try {
      InetAddress[] addresses = resolver.resolve(host);
      if (addresses == null || addresses.length == 0)
        throw new IllegalArgumentException("Destination host did not resolve");
      Arrays.stream(addresses).forEach(UrlSafety::validatePublicAddress);
    } catch (UnknownHostException e) { throw new IllegalArgumentException("Invalid destination address", e); }
  }

  private static void validatePublicAddress(InetAddress ip) {
    if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress()
        || ip.isSiteLocalAddress() || ip.isMulticastAddress() || isReservedV4(ip.getAddress())
        || isReservedV6(ip.getAddress())) {
      throw new IllegalArgumentException("Non-public destination address");
    }
  }

  @FunctionalInterface
  interface AddressResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
  }

  private static boolean isReservedV4(byte[] ip) {
    if (ip.length != 4) return false;
    int first = Byte.toUnsignedInt(ip[0]);
    int second = Byte.toUnsignedInt(ip[1]);
    int third = Byte.toUnsignedInt(ip[2]);
    return first == 0 || first == 10 || first == 127 || first >= 224
        || (first == 100 && second >= 64 && second <= 127)
        || (first == 169 && second == 254)
        || (first == 172 && second >= 16 && second <= 31)
        || (first == 192 && (second == 0 || (second == 0 && third == 2) || (second == 88 && third == 99) || second == 168))
        || (first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
        || (first == 203 && second == 0 && third == 113);
  }

  private static boolean isReservedV6(byte[] ip) {
    if (ip.length != 16) return false;
    int first = Byte.toUnsignedInt(ip[0]);
    int second = Byte.toUnsignedInt(ip[1]);
    return (first & 0xfe) == 0xfc || (first == 0x20 && second == 0x01
        && Byte.toUnsignedInt(ip[2]) == 0x0d && Byte.toUnsignedInt(ip[3]) == 0xb8);
  }
}
