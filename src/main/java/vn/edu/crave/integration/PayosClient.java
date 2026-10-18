package vn.edu.crave.integration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.crave.integration.PayosJson;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Native Java client for https://payos.vn/docs/api/ payment requests. */
public class PayosClient {
    private static final URI API_URI = URI.create("https://api-merchant.payos.vn/");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_BODY_BYTES = 256 * 1024;
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final Set<String> STATUSES = Set.of("PENDING", "PROCESSING", "PAID", "CANCELLED", "EXPIRED");

    private final String clientId;
    private final String apiKey;
    private final byte[] checksumKey;
    private final URI baseUri;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public PayosClient(String clientId, String apiKey, String checksumKey) {
        this(clientId, apiKey, checksumKey, API_URI,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER).build(), DEFAULT_TIMEOUT);
    }

    /** Endpoint and transport injection are intended for local fixtures. */
    public PayosClient(String clientId, String apiKey, String checksumKey, URI baseUri, HttpClient httpClient) {
        this(clientId, apiKey, checksumKey, baseUri, httpClient, DEFAULT_TIMEOUT);
    }

    public PayosClient(String clientId, String apiKey, String checksumKey, URI baseUri,
                       HttpClient httpClient, Duration timeout) {
        this.clientId = credential(clientId);
        this.apiKey = credential(apiKey);
        this.checksumKey = credential(checksumKey).getBytes(StandardCharsets.UTF_8);
        this.baseUri = apiUri(baseUri);
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client is required.");
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Payment requests must not follow redirects.");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Invalid payment request timeout.");
        }
        this.timeout = timeout;
        this.mapper = PayosJson.objectMapper().copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32).maxStringLength(64 * 1024).maxNumberLength(20).build());
    }

    public JsonNode createPaymentLink(long orderCode, long amount, String description,
                                      String returnUrl, String cancelUrl) {
        positive(orderCode);
        positive(amount);
        if (description == null || description.isBlank() || description.length() > 255) {
            throw new IllegalArgumentException("Invalid payment description.");
        }
        callbackUri(returnUrl);
        callbackUri(cancelUrl);
        ObjectNode request = mapper.createObjectNode().put("amount", amount).put("cancelUrl", cancelUrl)
                .put("description", description).put("orderCode", orderCode).put("returnUrl", returnUrl);
        request.put("signature", signature(request));
        JsonNode data = exchange("POST", "v2/payment-requests", request);
        validateLink(data, orderCode);
        if (integer(data, "amount", 502) != amount || !"VND".equals(text(data, "currency", 502))) {
            throw invalidResponse();
        }
        text(data, "paymentLinkId", 502);
        String checkoutUrl = text(data, "checkoutUrl", 502);
        URI checkout;
        try {
            checkout = URI.create(checkoutUrl);
        } catch (IllegalArgumentException exception) {
            throw invalidResponse();
        }
        if (!"https".equalsIgnoreCase(checkout.getScheme()) || !"pay.payos.vn".equalsIgnoreCase(checkout.getHost())
                || checkout.getUserInfo() != null || (checkout.getPort() != -1 && checkout.getPort() != 443)) {
            throw invalidResponse();
        }
        return data;
    }

    public JsonNode getPaymentLink(long orderCode) {
        positive(orderCode);
        JsonNode data = exchange("GET", "v2/payment-requests/" + orderCode, null);
        validatePaymentInformation(data, orderCode);
        return data;
    }

    public JsonNode cancelPaymentLink(long orderCode, String reason) {
        positive(orderCode);
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new IllegalArgumentException("Invalid payment cancellation reason.");
        }
        JsonNode data = exchange("POST", "v2/payment-requests/" + orderCode + "/cancel",
                mapper.createObjectNode().put("cancellationReason", reason));
        validatePaymentInformation(data, orderCode);
        return data;
    }

    /** Only data is signed. The outer success/code fields cannot establish payment success. */
    public JsonNode verifyWebhook(JsonNode envelope) {
        validateWebhookSize(envelope, 0, new int[]{MAX_BODY_BYTES});
        JsonNode data = verifiedData(envelope, 400);
        if (integer(data, "orderCode", 400) <= 0) {
            throw new PayosException("INVALID_WEBHOOK", 400);
        }
        integer(data, "amount", 400);
        if (!"VND".equals(text(data, "currency", 400))) {
            throw new PayosException("INVALID_WEBHOOK", 400);
        }
        text(data, "paymentLinkId", 400);
        text(data, "code", 400);
        text(data, "reference", 400);
        return data;
    }

    private JsonNode exchange(String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(timeout)
                    .header("Accept", "application/json").header("x-client-id", clientId).header("x-api-key", apiKey);
            if (body == null) {
                request.GET();
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)));
            }
            CompletableFuture<HttpResponse<byte[]>> pending = httpClient.sendAsync(request.build(),
                    info -> new LimitedBodySubscriber());
            HttpResponse<byte[]> response;
            try {
                // Unlike an InputStream body handler, this deadline covers the complete body as well as headers.
                response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                pending.cancel(true);
                throw new PayosException("TIMEOUT", 503);
            } catch (InterruptedException exception) {
                pending.cancel(true);
                Thread.currentThread().interrupt();
                throw new PayosException("INTERRUPTED", 503);
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof HttpTimeoutException) {
                    throw new PayosException("TIMEOUT", 503);
                }
                throw new PayosException("PROVIDER_UNAVAILABLE", 503);
            }
            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                throw new PayosException("PROVIDER_UNAVAILABLE", 503);
            }
            if (status >= 300 && status < 400) {
                throw new PayosException("INVALID_RESPONSE", 502);
            }
            byte[] responseBody = response.body();
            if (responseBody == null || responseBody.length == 0 || responseBody.length > MAX_BODY_BYTES) {
                throw invalidResponse();
            }
            JsonNode envelope = mapper.readTree(responseBody);
            if (envelope == null || !envelope.isObject() || !envelope.path("code").isTextual()) {
                throw invalidResponse();
            }
            String code = envelope.path("code").textValue();
            if (!"00".equals(code)) {
                // Numeric error codes are the only provider-controlled content exposed to callers.
                throw new PayosException(code.matches("[0-9]{2,5}") ? code : "PROVIDER_ERROR", 502);
            }
            if (status < 200 || status >= 300) {
                throw invalidResponse();
            }
            return verifiedData(envelope, 502);
        } catch (IOException exception) {
            throw invalidResponse();
        }
    }

    private JsonNode verifiedData(JsonNode envelope, int status) {
        if (envelope == null || !envelope.isObject() || !envelope.path("data").isObject()
                || !envelope.path("signature").isTextual()) {
            throw new PayosException("INVALID_SIGNATURE", status);
        }
        JsonNode data = envelope.path("data");
        String supplied = envelope.path("signature").textValue();
        if (!supplied.matches("[0-9a-fA-F]{64}")) {
            throw new PayosException("INVALID_SIGNATURE", status);
        }
        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(signature(data));
        } catch (PayosException exception) {
            throw new PayosException("INVALID_SIGNATURE", status);
        }
        if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(supplied))) {
            throw new PayosException("INVALID_SIGNATURE", status);
        }
        return data.deepCopy();
    }

    private void validatePaymentInformation(JsonNode data, long expectedCode) {
        validateLink(data, expectedCode);
        text(data, "id", 502);
        long amount = integer(data, "amount", 502);
        long paid = integer(data, "amountPaid", 502);
        long remaining = integer(data, "amountRemaining", 502);
        if (remaining != Math.max(0, amount - paid)
                || ("PAID".equals(data.path("status").textValue()) && paid < amount)) {
            throw invalidResponse();
        }
        JsonNode transactions = data.get("transactions");
        if (transactions == null || (!transactions.isArray() && !(transactions.isObject() && transactions.isEmpty()))) {
            throw invalidResponse();
        }
    }

    private void validateLink(JsonNode data, long expectedCode) {
        if (integer(data, "orderCode", 502) != expectedCode || integer(data, "amount", 502) <= 0
                || !STATUSES.contains(text(data, "status", 502))) {
            throw invalidResponse();
        }
        if (data.has("currency") && !"VND".equals(text(data, "currency", 502))) {
            throw invalidResponse();
        }
    }

    /** payment-requests signing uses raw values without URL encoding. */
    private String signature(JsonNode data) {
        StringBuilder canonical = new StringBuilder();
        TreeSet<String> fields = new TreeSet<>();
        data.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            if (canonical.length() > 0) canonical.append('&');
            canonical.append(field).append('=').append(signatureValue(data.get(field)));
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(checksumKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new PayosException("SIGNATURE_UNAVAILABLE", 503);
        }
    }

    private String signatureValue(JsonNode value) {
        if (value == null || value.isNull()
                || (value.isTextual() && Set.of("null", "undefined").contains(value.textValue()))) return "";
        if (value.isValueNode()) return value.asText();
        if (value.isObject() && value.isEmpty()) return "{}";
        if (!value.isArray()) throw invalidResponse();
        ArrayNode sorted = mapper.createArrayNode();
        for (JsonNode item : value) {
            if (!item.isObject()) throw invalidResponse();
            ObjectNode entry = mapper.createObjectNode();
            TreeSet<String> keys = new TreeSet<>();
            item.fieldNames().forEachRemaining(keys::add);
            keys.forEach(key -> entry.set(key, item.get(key)));
            sorted.add(entry);
        }
        return sorted.toString();
    }

    private static long integer(JsonNode object, String field, int status) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 0 || value.longValue() > MAX_SAFE_INTEGER) {
            throw new PayosException(status == 400 ? "INVALID_WEBHOOK" : "INVALID_RESPONSE", status);
        }
        return value.longValue();
    }

    private static String text(JsonNode object, String field, int status) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 2048) {
            throw new PayosException(status == 400 ? "INVALID_WEBHOOK" : "INVALID_RESPONSE", status);
        }
        return value.textValue();
    }

    private static void positive(long value) {
        if (value <= 0 || value > MAX_SAFE_INTEGER) throw new IllegalArgumentException("Invalid payment integer.");
    }

    private static String credential(String value) {
        if (value == null || value.isBlank() || value.length() > 512
                || value.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("Payment credentials are missing or invalid.");
        }
        return value;
    }

    private static URI apiUri(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme())
                && Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost())))) {
            throw new IllegalArgumentException("Invalid payment API endpoint.");
        }
        return URI.create(uri.toString().endsWith("/") ? uri.toString() : uri + "/");
    }

    private static void callbackUri(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || value.length() > 2048
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid payment callback URL.");
        }
    }

    private static PayosException invalidResponse() {
        return new PayosException("INVALID_RESPONSE", 502);
    }

    private static void validateWebhookSize(JsonNode node, int depth, int[] budget) {
        if (node == null || depth > 32 || (budget[0] -= 8) < 0) {
            throw new PayosException("INVALID_WEBHOOK", 400);
        }
        if (node.isTextual()) {
            if (node.textValue().length() > 64 * 1024
                    || (budget[0] -= node.textValue().getBytes(StandardCharsets.UTF_8).length) < 0) {
                throw new PayosException("INVALID_WEBHOOK", 400);
            }
        } else if (node.isContainerNode()) {
            if (node.isObject()) {
                var fields = node.fields();
                while (fields.hasNext()) {
                    var field = fields.next();
                    if (field.getKey().length() > 256 || (budget[0] -= field.getKey().length() * 3) < 0) {
                        throw new PayosException("INVALID_WEBHOOK", 400);
                    }
                    validateWebhookSize(field.getValue(), depth + 1, budget);
                }
            } else {
                for (JsonNode child : node) validateWebhookSize(child, depth + 1, budget);
            }
        }
    }

    /** Limits memory before JSON parsing; cancellation also stops oversized response streams. */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_BODY_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Payment response exceeds the size limit."));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable throwable) { body.completeExceptionally(throwable); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
