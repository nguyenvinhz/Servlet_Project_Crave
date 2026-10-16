package com.foodordering.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodordering.util.IntegrationJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PayosClientTest {
    private static final String CHECKSUM = "fixture-checksum-key";
    private HttpServer server;
    private URI baseUri;
    private PayosClient client;
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    private final AtomicReference<String> clientId = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicInteger httpStatus = new AtomicInteger(200);
    private final AtomicInteger redirectHits = new AtomicInteger();

    @BeforeEach
    void startFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/payment-requests", this::respond);
        server.createContext("/redirect", exchange -> {
            redirectHits.incrementAndGet();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        client = fixtureClient(Duration.ofSeconds(2));
    }

    @AfterEach
    void stopFixture() {
        server.stop(0);
    }

    @Test
    void createsSignedRequestAndReturnsVerifiedLink() throws Exception {
        response.set(signed(createData()));
        JsonNode result = client.createPaymentLink(123, 3000, "CRAVE123", "https://shop.example/return?a=1&b=2",
                "https://shop.example/cancel");
        assertEquals(123, result.path("orderCode").longValue());
        assertEquals("POST", method.get());
        assertEquals("/v2/payment-requests", path.get());
        assertEquals("fixture-client-id", clientId.get());
        assertEquals("fixture-api-key", apiKey.get());
        assertEquals(hmac("amount=3000&cancelUrl=https://shop.example/cancel&description=CRAVE123"
                + "&orderCode=123&returnUrl=https://shop.example/return?a=1&b=2", CHECKSUM),
                requestBody.get().path("signature").textValue());
    }

    @Test
    void validatesReadAndCancelResponsesIncludingSortedTransactionFields() throws Exception {
        ObjectNode data = informationData();
        data.putArray("transactions").addObject().put("reference", "TX01").put("amount", 1000);
        // The fixture signs an explicit provider string; array element keys must be sorted.
        String canonical = "amount=3000&amountPaid=0&amountRemaining=3000&id=link123&orderCode=123"
                + "&status=PENDING&transactions=[{\"amount\":1000,\"reference\":\"TX01\"}]";
        response.set(envelope(data, hmac(canonical, CHECKSUM)).toString());
        assertEquals("link123", client.getPaymentLink(123).path("id").textValue());
        assertEquals("GET", method.get());
        assertEquals("/v2/payment-requests/123", path.get());
        data.put("status", "CANCELLED");
        response.set(signed(data));
        assertEquals("CANCELLED", client.cancelPaymentLink(123, "Customer requested cancellation")
                .path("status").textValue());
        assertEquals("POST", method.get());
        assertEquals("/v2/payment-requests/123/cancel", path.get());
        assertEquals("Customer requested cancellation", requestBody.get().path("cancellationReason").textValue());
    }

    @Test
    void rejectsTamperingAndMissingSignaturesOnEveryPaymentEndpoint() throws Exception {
        ObjectNode envelope = IntegrationJson.objectMapper().readValue(signed(createData()), ObjectNode.class);
        ((ObjectNode) envelope.path("data")).put("amount", 3001);
        response.set(envelope.toString());
        assertEquals("INVALID_SIGNATURE", assertThrows(PayosException.class,
                () -> client.createPaymentLink(123, 3000, "CRAVE123", "https://shop.example/return",
                        "https://shop.example/cancel")).getCode());
        response.set(envelope(informationData(), "a".repeat(64)).toString());
        assertEquals("INVALID_SIGNATURE", assertThrows(PayosException.class, () -> client.getPaymentLink(123)).getCode());
        response.set(IntegrationJson.objectMapper().createObjectNode().put("code", "00")
                .set("data", informationData()).toString());
        assertEquals("INVALID_SIGNATURE", assertThrows(PayosException.class,
                () -> client.cancelPaymentLink(123, "Cancel")).getCode());
    }

    @Test
    void rejectsSignedMismatchedOrderAmountCurrencyAndUnsafeCheckoutUrl() throws Exception {
        for (ObjectNode data : new ObjectNode[]{createData().put("orderCode", 124),
                createData().put("amount", 3001), createData().put("currency", "USD"),
                createData().put("checkoutUrl", "https://evil.example/payment")}) {
            response.set(signed(data));
            assertEquals("INVALID_RESPONSE", assertThrows(PayosException.class,
                    () -> client.createPaymentLink(123, 3000, "CRAVE123", "https://shop.example/return",
                            "https://shop.example/cancel")).getCode());
        }
        response.set(signed(informationData().put("amountPaid", "3000")));
        assertThrows(PayosException.class, () -> client.getPaymentLink(123));
    }

    @Test
    void preservesOnlySafeProviderCodeForDuplicateHandling() {
        response.set("{\"code\":\"231\",\"desc\":\"fixture-api-key fixture-checksum-key secret details\"}");
        PayosException duplicate = assertThrows(PayosException.class, () -> client.getPaymentLink(123));
        assertEquals("231", duplicate.getCode());
        assertEquals(502, duplicate.getStatusCode());
        assertFalse(duplicate.toString().contains("fixture"));
        assertNull(duplicate.getCause());
        response.set("{\"code\":\"fixture-api-key\",\"desc\":\"secret\"}");
        assertEquals("PROVIDER_ERROR", assertThrows(PayosException.class, () -> client.getPaymentLink(123)).getCode());
    }

    @Test
    void rejectsRedirectsAndMapsRateLimitsWithoutExposingBody() {
        response.set("untrusted provider output");
        httpStatus.set(302);
        assertEquals(502, assertThrows(PayosException.class, () -> client.getPaymentLink(123)).getStatusCode());
        assertEquals(0, redirectHits.get());
        httpStatus.set(429);
        assertEquals(503, assertThrows(PayosException.class, () -> client.getPaymentLink(123)).getStatusCode());
        httpStatus.set(503);
        assertEquals(503, assertThrows(PayosException.class, () -> client.getPaymentLink(123)).getStatusCode());
    }

    @Test
    void rejectsMalformedDuplicateTrailingAndOversizedJson() {
        for (String malformed : new String[]{"not-json", "[]", "{\"code\":\"231\",\"code\":\"00\"}",
                "{\"code\":\"231\"} {}", "x".repeat(300 * 1024)}) {
            response.set(malformed);
            assertThrows(PayosException.class, () -> client.getPaymentLink(123));
        }
    }

    @Test
    void timesOutWhenHeadersArriveButBodyStalls() {
        server.createContext("/slow/v2/payment-requests", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        PayosClient shortTimeout = new PayosClient("fixture-client-id", "fixture-api-key", CHECKSUM,
                baseUri.resolve("slow/"), transport(), Duration.ofMillis(150));
        PayosException failure = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> assertThrows(PayosException.class, () -> shortTimeout.getPaymentLink(123)));
        assertEquals("TIMEOUT", failure.getCode());
        assertEquals(503, failure.getStatusCode());
    }

    @Test
    void verifiesOfficialWebhookVectorAndIgnoresUnsignedEnvelopeFlags() throws Exception {
        // Published interoperability vector: https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/
        String publicExampleKey = "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
        JsonNode data = IntegrationJson.objectMapper().readTree("""
                {"orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678",
                "reference":"TF230204212323","transactionDateTime":"2023-02-04 18:25:00","currency":"VND",
                "paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00","desc":"Thành công",
                "counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"",
                "counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""}
                """);
        ObjectNode envelope = envelope(data, "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03");
        envelope.put("success", false).put("code", "99");
        PayosClient publicVector = new PayosClient("fixture-client-id", "fixture-api-key", publicExampleKey,
                baseUri, transport());
        JsonNode verified = publicVector.verifyWebhook(envelope);
        assertEquals("00", verified.path("code").textValue());
        ((ObjectNode) verified).put("amount", 1);
        assertEquals(3000, envelope.path("data").path("amount").longValue());
        ((ObjectNode) envelope.path("data")).put("amount", 1);
        assertEquals(400, assertThrows(PayosException.class, () -> publicVector.verifyWebhook(envelope)).getStatusCode());
    }

    @Test
    void rejectsInvalidWebhookShapeAndDoesNotConvertSignedFailureToSuccess() throws Exception {
        ObjectNode data = webhookData().put("code", "99");
        assertEquals("99", client.verifyWebhook(IntegrationJson.objectMapper().readTree(signed(data)))
                .path("code").textValue());
        assertThrows(PayosException.class, () -> client.verifyWebhook(envelope(data, "short")));
        for (ObjectNode invalid : new ObjectNode[]{webhookData().put("currency", "USD"),
                webhookData().put("amount", -1), webhookData().put("orderCode", "123")}) {
            assertEquals(400, assertThrows(PayosException.class,
                    () -> client.verifyWebhook(IntegrationJson.objectMapper().readTree(signed(invalid)))).getStatusCode());
        }
    }

    @Test
    void disallowsRedirectingTransportAndInvalidCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new PayosClient("id", "key", "checksum", baseUri,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()));
        assertThrows(IllegalArgumentException.class, () -> new PayosClient("id\nsecret", "key", "checksum"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        try {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            clientId.set(exchange.getRequestHeaders().getFirst("x-client-id"));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] request = exchange.getRequestBody().readAllBytes();
            requestBody.set(request.length == 0 ? null : IntegrationJson.objectMapper().readTree(request));
            if (httpStatus.get() == 302) exchange.getResponseHeaders().set("Location", baseUri.resolve("redirect").toString());
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(httpStatus.get(), body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private PayosClient fixtureClient(Duration timeout) {
        return new PayosClient("fixture-client-id", "fixture-api-key", CHECKSUM, baseUri, transport(), timeout);
    }

    private static HttpClient transport() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(1)).build();
    }

    private static ObjectNode createData() {
        return IntegrationJson.objectMapper().createObjectNode().put("orderCode", 123).put("amount", 3000)
                .put("currency", "VND").put("paymentLinkId", "link123").put("status", "PENDING")
                .put("checkoutUrl", "https://pay.payos.vn/web/link123");
    }

    private static ObjectNode informationData() {
        ObjectNode data = IntegrationJson.objectMapper().createObjectNode().put("orderCode", 123).put("amount", 3000)
                .put("id", "link123").put("status", "PENDING").put("amountPaid", 0).put("amountRemaining", 3000);
        data.putArray("transactions");
        return data;
    }

    private static ObjectNode webhookData() {
        return IntegrationJson.objectMapper().createObjectNode().put("orderCode", 123).put("amount", 3000)
                .put("currency", "VND").put("paymentLinkId", "link123").put("code", "00").put("reference", "TX01");
    }

    private static ObjectNode envelope(JsonNode data, String signature) {
        ObjectNode envelope = IntegrationJson.objectMapper().createObjectNode().put("code", "00")
                .put("success", true).put("signature", signature);
        envelope.set("data", data);
        return envelope;
    }

    private static String signed(JsonNode data) throws Exception {
        java.util.TreeMap<String, JsonNode> fields = new java.util.TreeMap<>();
        data.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
        String canonical = fields.entrySet().stream().map(entry -> entry.getKey() + "="
                + (entry.getValue().isContainerNode() ? sortedArray(entry.getValue()) : entry.getValue().asText()))
                .collect(java.util.stream.Collectors.joining("&"));
        return envelope(data, hmac(canonical, CHECKSUM)).toString();
    }

    private static String sortedArray(JsonNode data) {
        if (!data.isArray()) return data.toString();
        var sorted = IntegrationJson.objectMapper().createArrayNode();
        for (JsonNode element : data) {
            java.util.TreeMap<String, JsonNode> fields = new java.util.TreeMap<>();
            element.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            ObjectNode value = IntegrationJson.objectMapper().createObjectNode();
            fields.forEach(value::set);
            sorted.add(value);
        }
        return sorted.toString();
    }

    private static String hmac(String value, String key) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(hmac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
