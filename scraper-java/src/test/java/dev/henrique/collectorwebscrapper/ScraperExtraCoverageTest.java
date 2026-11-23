package dev.henrique.collectorwebscrapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScraperExtraCoverageTest {

    @Test
    void urlSafetyCoversAllReservedV4AndV6AndValidationBranches() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl(null));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("https://e.com/" + "a".repeat(2005)));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("http://[::1"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("/relative"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("ftp://e.com"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("https:///nohost"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateOutputUrl("https://user:pass@e.com"));
        assertNotNull(UrlSafety.validateOutputUrl("http://example.com"));

        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateSourcePageUrl("hacker-news", "http://news.ycombinator.com/item"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateSourcePageUrl("hacker-news", "https://news.ycombinator.com:8443/item"));
        assertNotNull(UrlSafety.validateSourcePageUrl("hacker-news", "https://news.ycombinator.com:443/item"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateSourcePageUrl("hacker-news", "https://other.com/item"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateSourcePageUrl("hacker-news", "https://news.ycombinator.com/other"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateSourcePageUrl("wordpress-news", "https://other.com/news/1"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateSourcePageUrl("wordpress-news", "https://wordpress.org/other"));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateSourcePageUrl("unknown", "https://wordpress.org/news/1"));

        // validateResolvedAddress branches
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateResolvedAddress("h", host -> null));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateResolvedAddress("h", host -> new InetAddress[0]));
        assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateResolvedAddress("h", host -> {
            throw new UnknownHostException("fail");
        }));

        // Reserved IPv4 and IPv6 addresses
        byte[][] badIps = new byte[][]{
                {0, 0, 0, 0},
                {0, 1, 2, 3},
                {10, 0, 0, 1},
                {127, 0, 0, 1},
                {(byte) 169, (byte) 254, 1, 1},
                {(byte) 172, 16, 0, 1},
                {(byte) 172, 31, 0, 1},
                {(byte) 192, (byte) 168, 1, 1},
                {(byte) 224, 0, 0, 1},
                {100, 64, 0, 1},
                {100, 127, 0, 1},
                {(byte) 192, 0, 0, 1},
                {(byte) 192, 0, 2, 1},
                {(byte) 192, 88, 99, 1},
                {(byte) 198, 18, 0, 1},
                {(byte) 198, 19, 0, 1},
                {(byte) 198, 51, 100, 1},
                {(byte) 203, 0, 113, 1},
                {(byte) 0xfc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                {(byte) 0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}
        };
        for (byte[] ip : badIps) {
            InetAddress addr = InetAddress.getByAddress("h", ip);
            assertThrows(IllegalArgumentException.class, () -> UrlSafety.validateResolvedAddress("h", host -> new InetAddress[]{addr}));
        }

        // Valid public IPv4 and IPv6 addresses covering non-reserved branches
        byte[][] goodIps = new byte[][]{
                {8, 8, 8, 8},
                {100, 63, 0, 1},
                {100, (byte) 128, 0, 1},
                {(byte) 169, 1, 1, 1},
                {(byte) 172, 15, 0, 1},
                {(byte) 172, 32, 0, 1},
                {(byte) 192, 1, 1, 1},
                {(byte) 192, 88, 1, 1},
                {(byte) 198, 20, 0, 1},
                {(byte) 198, 51, 1, 1},
                {(byte) 203, 1, 1, 1},
                {(byte) 203, 0, 1, 1},
                {0x26, 0x06, 0x47, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                {0x20, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                {0x20, 0x01, 0x0e, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
                {0x20, 0x01, 0x0d, 0x01, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}
        };
        for (byte[] ip : goodIps) {
            InetAddress addr = InetAddress.getByAddress("h", ip);
            assertDoesNotThrow(() -> UrlSafety.validateResolvedAddress("h", host -> new InetAddress[]{addr}));
        }
    }

    @Test
    void itemInputPolicyRobotsIdentityAndParserCoverAllBranches() {
        // CollectedItemInput
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput(null, "1", "t", "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("  ", "1", "t", "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("x".repeat(41), "1", "t", "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", null, "t", "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "  ", "t", "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "x".repeat(2001), "t", "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "1", null, "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "1", "  ", "https://e.com", null));
        assertThrows(IllegalArgumentException.class, () -> new CollectedItemInput("s", "1", "x".repeat(1001), "https://e.com", null));
        assertNotNull(new CollectedItemInput("s", "1", "t", "https://e.com", null));

        // SourceAccessPolicy
        assertThrows(IllegalArgumentException.class, () -> SourceAccessPolicy.validateResponseSize(-1));
        assertThrows(IllegalArgumentException.class, () -> SourceAccessPolicy.validateResponseSize(SourceAccessPolicy.MAX_RESPONSE_BYTES + 1));
        assertDoesNotThrow(() -> SourceAccessPolicy.validateResponseSize(100));
        assertTrue(SourceAccessPolicy.sameOriginRedirect("hacker-news", "https://news.ycombinator.com/item", "https://news.ycombinator.com/item?id=1"));
        assertFalse(SourceAccessPolicy.sameOriginRedirect("hacker-news", "https://news.ycombinator.com/item", "https://evil.com/item"));
        assertEquals(SourceAccessPolicy.FetchStatus.OK, SourceAccessPolicy.classify(200, false, true));
        assertEquals(SourceAccessPolicy.FetchStatus.INVALID_RESPONSE, SourceAccessPolicy.classify(199, false, true));
        assertEquals(SourceAccessPolicy.FetchStatus.INVALID_RESPONSE, SourceAccessPolicy.classify(302, false, true));
        assertEquals(SourceAccessPolicy.FetchStatus.INVALID_RESPONSE, SourceAccessPolicy.classify(200, false, false));

        // RobotsRules
        RobotsRules r = RobotsRules.parse("invalid line without colon\nUser-agent: googlebot\nDisallow: /no\nCrawl-delay: 5\nUser-agent: *\nDisallow:   \nDisallow: /private\nCrawl-delay: not-a-number\nOther: val");
        assertFalse(r.allows("/private/page"));
        assertTrue(r.allows("/no"));
        assertEquals(0, r.crawlDelaySeconds());

        // Identity
        assertNotNull(Identity.fingerprint("s", "t", null, null, "https://e.com/path?a=1&utm_source=x&fbclid=y"));

        // SourceParser branches
        assertThrows(IllegalArgumentException.class, () -> SourceParser.parse("hacker-news", "https://news.ycombinator.com/item", null));
        assertThrows(IllegalArgumentException.class, () -> SourceParser.parse("hacker-news", "https://news.ycombinator.com/item", "x".repeat(SourceAccessPolicy.MAX_RESPONSE_BYTES + 10)));
        try {
            java.lang.reflect.Method isResV4 = UrlSafety.class.getDeclaredMethod("isReservedV4", byte[].class);
            isResV4.setAccessible(true);
            assertFalse((Boolean) isResV4.invoke(null, (Object) new byte[]{1, 2}));
            java.lang.reflect.Method isResV6 = UrlSafety.class.getDeclaredMethod("isReservedV6", byte[].class);
            isResV6.setAccessible(true);
            assertFalse((Boolean) isResV6.invoke(null, (Object) new byte[]{1, 2}));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThrows(IllegalArgumentException.class, () -> SourceParser.parse("hacker-news", "https://news.ycombinator.com/item", "<html></html>"));
        String hnFallbackHtml = "<html><body><table><tr class='athing' id='abc'><td><span class='titleline'><a>Story Title</a></span></td></tr></table><div class='comment-tree'><span class='commtext'>   </span></div></body></html>";
        CollectedEnvelope env1 = SourceParser.parse("hacker-news", "https://news.ycombinator.com/item", hnFallbackHtml);
        assertTrue(env1.item().externalId().startsWith("fingerprint:v1:"));

        String hnEmptyTitle = "<html><body><table><tr class='athing' id='123'><td><span class='titleline'><a href='https://e.com'>   </a></span></td></tr></table></body></html>";
        assertThrows(IllegalArgumentException.class, () -> SourceParser.parse("hacker-news", "https://news.ycombinator.com/item", hnEmptyTitle));

        String hnWithComments = "<html><body><table><tr class='athing' id='123'><td><span class='titleline'><a href='https://e.com'>T</a></span></td></tr></table><div class='comment-tree'><span class='commtext'> valid comment </span></div></body></html>";
        assertEquals(1, SourceParser.parse("hacker-news", "https://news.ycombinator.com/item", hnWithComments).pageContent().comments().size());
        assertThrows(IllegalArgumentException.class, () -> SourceParser.parse("wordpress-news", "https://wordpress.org/news/1", "<html></html>"));
        assertThrows(IllegalArgumentException.class, () -> SourceParser.parse("wordpress-news", "https://wordpress.org/news/1", "<article><h1 class='entry-title'>T</h1></article>"));
        String wpFallbackHtml = "<article class='post-abc'><h1 class='entry-title'>WP Title</h1><div class='entry-content'>Body</div><div class='comment-content'>   </div></article>";
        CollectedEnvelope env2 = SourceParser.parse("wordpress-news", "https://wordpress.org/news/1", wpFallbackHtml);
        assertTrue(env2.item().externalId().startsWith("fingerprint:v1:"));
        try {
            assertEquals(2, Main.run(new String[]{"parse"}));
            assertEquals(2, Main.run(new String[]{"parse", "a", "b", "c", "d"}));
            assertEquals(2, Main.run(new String[]{"other", "a", "b", "c"}));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
