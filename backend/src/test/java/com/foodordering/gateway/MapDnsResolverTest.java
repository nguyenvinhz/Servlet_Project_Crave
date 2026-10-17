package com.foodordering.gateway;

import org.junit.jupiter.api.Test;

import javax.naming.NamingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MapDnsResolverTest {
    @Test
    void successfulSystemResolutionNeverContactsTheFallback() throws Exception {
        InetAddress[] expected = {InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 7})};
        var resolver = new MapDnsResolver(host -> expected, host -> {
            fail("The system lookup succeeded");
            return new String[0];
        });

        assertSame(expected, resolver.resolve("nominatim.openstreetmap.org"));
        assertSame(expected, resolver.resolve("custom.example"));
    }

    @Test
    void customPrivateAndLookalikeHostsNeverUsePublicDns() {
        var fallbackCalls = new AtomicInteger();
        var original = new UnknownHostException("System lookup failed");
        var resolver = new MapDnsResolver(host -> { throw original; }, host -> {
            fallbackCalls.incrementAndGet();
            return new String[]{"203.0.113.7"};
        });

        for (String host : new String[]{"localhost", "maps.internal", "custom.example", "127.0.0.1",
                "nominatim.openstreetmap.org.example", "photon.komoot.io.example", "a.tile.openstreetmap.org"}) {
            assertSame(original, assertThrows(UnknownHostException.class, () -> resolver.resolve(host)));
        }
        assertEquals(0, fallbackCalls.get());
    }

    @Test
    void publicMapHostsUseValidatedRecordsAndKeepTheirOriginalHostname() throws Exception {
        var fallbackCalls = new AtomicInteger();
        var resolver = new MapDnsResolver(host -> { throw new UnknownHostException(host); }, host -> {
            fallbackCalls.incrementAndGet();
            return new String[]{"203.0.113.7", "not-an-ip.example", "999.0.0.1", "1.2.3", "1.2.3.4.5",
                    "-1.2.3.4", "1.2.3.4 ", "2001:db8::1", null, "198.51.100.8"};
        });

        for (String host : new String[]{"nominatim.openstreetmap.org", "NOMINATIM.OPENSTREETMAP.ORG",
                "photon.komoot.io", "tile.openstreetmap.org"}) {
            InetAddress[] actual = resolver.resolve(host);
            assertEquals(2, actual.length);
            assertEquals("203.0.113.7", actual[0].getHostAddress());
            assertEquals("198.51.100.8", actual[1].getHostAddress());
            assertEquals(host, actual[0].getHostName());
            assertEquals(host, resolver.resolveCanonicalHostname(host));
        }
        assertEquals(4, fallbackCalls.get());
    }

    @Test
    void aFailedFallbackPreservesTheOriginalSystemFailureAndItsCause() {
        var original = new UnknownHostException("System DNS unavailable");
        var fallbackFailure = new NamingException("Public DNS unavailable");
        var resolver = new MapDnsResolver(host -> { throw original; }, host -> { throw fallbackFailure; });

        var actual = assertThrows(UnknownHostException.class, () -> resolver.resolve("nominatim.openstreetmap.org"));
        assertSame(original, actual);
        assertArrayEquals(new Throwable[]{fallbackFailure}, actual.getSuppressed());
    }

    @Test
    void emptyAndInvalidDnsRecordsCannotBecomeAnotherHostnameLookup() {
        for (String[] records : new String[][]{new String[0], {"maps.internal", "256.1.2.3", "1.2.3"}}) {
            var original = new UnknownHostException("System DNS unavailable");
            var resolver = new MapDnsResolver(host -> { throw original; }, host -> records);

            var actual = assertThrows(UnknownHostException.class, () -> resolver.resolve("nominatim.openstreetmap.org"));
            assertSame(original, actual);
            assertEquals(1, actual.getSuppressed().length);
            assertInstanceOf(UnknownHostException.class, actual.getSuppressed()[0]);
        }
    }

    @Test
    void canonicalHostnameNeverMakesAnotherDnsRequest() {
        var resolver = new MapDnsResolver(host -> {
            fail("Canonical hostname must keep the TLS peer name");
            return new InetAddress[0];
        }, host -> {
            fail("Canonical hostname must not query public DNS");
            return new String[0];
        });

        assertEquals("nominatim.openstreetmap.org", resolver.resolveCanonicalHostname("nominatim.openstreetmap.org"));
        assertEquals("custom.example", resolver.resolveCanonicalHostname("custom.example"));
    }
}
