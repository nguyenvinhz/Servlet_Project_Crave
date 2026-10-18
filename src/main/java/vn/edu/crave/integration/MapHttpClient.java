package vn.edu.crave.integration;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.cache.CachingHttpClients;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** HTTP transport for provider-native map responses, with normal TLS verification. */
public final class MapHttpClient implements AutoCloseable {
    public record Reply(int status, byte[] body, Map<String, String> headers) { }

    private final CloseableHttpClient client;

    public MapHttpClient() { this(false); }

    public MapHttpClient(boolean cacheTiles) {
        HttpClientBuilder builder = cacheTiles ? CachingHttpClients.custom().setCacheConfig(CacheConfig.custom()
                .setMaxCacheEntries(128).setMaxObjectSize(1024 * 1024)
                .setHeuristicCachingEnabled(true).setHeuristicDefaultLifetime(TimeValue.ofDays(7))
                .setSharedCache(true).setAsynchronousWorkers(0).build()) : HttpClients.custom();
        client = builder
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(new MapDnsResolver())
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(3))
                            .setSocketTimeout(Timeout.ofSeconds(5))
                            .build())
                    .build())
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.ofSeconds(3))
                    .setResponseTimeout(Timeout.ofSeconds(5))
                    .build())
            .setUserAgent("Crave-Food-Ordering/1.0 (+https://github.com/nguyenvinhz/Servlet_Project_Crave)")
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .build();
    }

    public Reply get(URI uri, String accept, int maxBytes) throws IOException {
        return get(uri, accept, maxBytes, Map.of());
    }

    public Reply get(URI uri, String accept, int maxBytes, Map<String, String> headers) throws IOException {
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid response size limit");
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", accept);
        headers.forEach(request::setHeader);
        return client.execute(request, response -> {
            byte[] body = new byte[0];
            if (response.getEntity() != null) {
                if (response.getEntity().getContentLength() > maxBytes) {
                    request.cancel();
                    throw new IOException("Map service response is too large");
                }
                try (var input = response.getEntity().getContent()) {
                    body = input.readNBytes(maxBytes + 1);
                    if (body.length > maxBytes) {
                        request.cancel();
                        throw new IOException("Map service response is too large");
                    }
                }
            }
            Map<String, String> responseHeaders = new LinkedHashMap<>();
            for (String name : List.of("Cache-Control", "Expires", "ETag", "Last-Modified", "Age", "Date")) {
                var header = response.getFirstHeader(name);
                if (header != null) responseHeaders.put(name, header.getValue());
            }
            return new Reply(response.getCode(), body, Map.copyOf(responseHeaders));
        });
    }

    @Override
    public void close() throws IOException { client.close(); }
}
