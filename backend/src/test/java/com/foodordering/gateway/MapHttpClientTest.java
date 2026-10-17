package com.foodordering.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MapHttpClientTest {
    @Test
    void preservesXmlBytesAndSendsTheApplicationIdentity() throws Exception {
        try (Fixture provider = new Fixture(200, "<searchresults/>".getBytes(StandardCharsets.UTF_8));
             MapHttpClient client = new MapHttpClient()) {
            provider.server.removeContext("/");
            var userAgent = new AtomicReference<String>();
            var accept = new AtomicReference<String>();
            provider.server.createContext("/", exchange -> {
                userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                exchange.getResponseHeaders().set("Content-Type", "application/xml");
                exchange.sendResponseHeaders(200, provider.bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(provider.bytes); }
            });

            var reply = client.get(provider.endpoint, "application/xml", 32);

            assertEquals(200, reply.status());
            assertArrayEquals(provider.bytes, reply.body());
            assertTrue(userAgent.get().startsWith("Crave-Food-Ordering/1.0"));
            assertEquals("application/xml", accept.get());
        }
    }

    @Test
    void rejectsOversizedBodiesWithAndWithoutAContentLength() throws Exception {
        for (boolean chunked : new boolean[]{false, true}) {
            try (Fixture provider = new Fixture(200, new byte[33]);
                 MapHttpClient client = new MapHttpClient()) {
                provider.chunked = chunked;
                assertThrows(IOException.class, () -> client.get(provider.endpoint, "application/xml", 32));
                assertEquals(1, provider.calls.get());
            }
        }
    }

    @Test
    void doesNotFollowRedirectsToAnotherAddress() throws Exception {
        try (Fixture destination = new Fixture(200, "secret".getBytes(StandardCharsets.UTF_8));
             Fixture provider = new Fixture(302, new byte[0]);
             MapHttpClient client = new MapHttpClient()) {
            provider.redirect = destination.endpoint;

            var reply = client.get(provider.endpoint, "application/xml", 32);

            assertEquals(302, reply.status());
            assertEquals(1, provider.calls.get());
            assertEquals(0, destination.calls.get());
        }
    }

    @Test
    void returnsUpstreamFailureWithoutRetrying() throws Exception {
        byte[] error = "Unavailable".getBytes(StandardCharsets.UTF_8);
        try (Fixture provider = new Fixture(503, error);
             MapHttpClient client = new MapHttpClient()) {
            var reply = client.get(provider.endpoint, "application/xml", 32);

            assertEquals(503, reply.status());
            assertArrayEquals(error, reply.body());
            assertEquals(1, provider.calls.get());
        }
    }

    @Test
    void tileCacheReusesFreshPngAndRelaysOnlyApprovedCacheHeaders() throws Exception {
        byte[] png = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3};
        try (Fixture provider = new Fixture(200, png);
             MapHttpClient client = new MapHttpClient(true)) {
            provider.server.removeContext("/");
            AtomicReference<String> referer = new AtomicReference<>();
            provider.server.createContext("/", exchange -> {
                provider.calls.incrementAndGet();
                referer.set(exchange.getRequestHeaders().getFirst("Referer"));
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                exchange.getResponseHeaders().set("ETag", "\"tile-v1\"");
                exchange.getResponseHeaders().set("Set-Cookie", "provider-session=private; HttpOnly");
                exchange.getResponseHeaders().set("X-Provider-Internal", "private");
                exchange.sendResponseHeaders(200, png.length);
                try (var output = exchange.getResponseBody()) { output.write(png); }
            });
            Map<String, String> headers = Map.of("Referer", "http://localhost:8081/crave/checkout");

            var first = client.get(provider.endpoint, "image/png", 1024, headers);
            var cached = client.get(provider.endpoint, "image/png", 1024, headers);

            assertEquals(1, provider.calls.get());
            assertEquals("http://localhost:8081/crave/checkout", referer.get());
            assertEquals(200, first.status());
            assertEquals(200, cached.status());
            assertArrayEquals(png, first.body());
            assertArrayEquals(png, cached.body());
            assertEquals("public, max-age=3600", cached.headers().get("Cache-Control"));
            assertEquals("\"tile-v1\"", cached.headers().get("ETag"));
            assertTrue(cached.headers().containsKey("Date"));
            assertFalse(first.headers().containsKey("Set-Cookie"));
            assertFalse(cached.headers().containsKey("Set-Cookie"));
            assertFalse(cached.headers().containsKey("X-Provider-Internal"));
            assertThrows(UnsupportedOperationException.class, () -> cached.headers().put("Set-Cookie", "anything"));
        }
    }

    @Test
    void expiredTileRevalidatesWithEtagAndKeepsThePngAfterNotModified() throws Exception {
        byte[] png = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 4, 5, 6};
        try (Fixture provider = new Fixture(200, png);
             MapHttpClient client = new MapHttpClient(true)) {
            provider.server.removeContext("/");
            AtomicReference<String> conditionalEtag = new AtomicReference<>();
            provider.server.createContext("/", exchange -> {
                int call = provider.calls.incrementAndGet();
                exchange.getResponseHeaders().set("ETag", "\"tile-v2\"");
                exchange.getResponseHeaders().set("Cache-Control", call == 1 ? "public, max-age=0" : "public, max-age=3600");
                if (call == 1) {
                    exchange.getResponseHeaders().set("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, png.length);
                    try (var output = exchange.getResponseBody()) { output.write(png); }
                } else {
                    conditionalEtag.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
                    exchange.sendResponseHeaders(304, -1);
                    exchange.close();
                }
            });

            var initial = client.get(provider.endpoint, "image/png", 1024);
            var revalidated = client.get(provider.endpoint, "image/png", 1024);
            var fresh = client.get(provider.endpoint, "image/png", 1024);

            assertEquals(2, provider.calls.get(), "The third read uses the freshness supplied by the 304 response");
            assertEquals("\"tile-v2\"", conditionalEtag.get());
            assertEquals(200, initial.status());
            assertEquals(200, revalidated.status());
            assertEquals(200, fresh.status());
            assertArrayEquals(png, initial.body());
            assertArrayEquals(png, revalidated.body());
            assertArrayEquals(png, fresh.body());
            assertEquals("public, max-age=3600", revalidated.headers().get("Cache-Control"));
            assertEquals("public, max-age=3600", fresh.headers().get("Cache-Control"));
            assertEquals("\"tile-v2\"", fresh.headers().get("ETag"));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final HttpServer server;
        final URI endpoint;
        final byte[] bytes;
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean chunked;
        volatile URI redirect;

        Fixture(int status, byte[] bytes) throws IOException {
            this.bytes = bytes;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                if (redirect != null) exchange.getResponseHeaders().set("Location", redirect.toString());
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : chunked ? 0 : bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
            endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search");
        }

        @Override
        public void close() { server.stop(0); }
    }
}
