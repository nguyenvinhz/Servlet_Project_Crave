package com.foodordering.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodordering.config.PayosConfig;
import com.foodordering.exception.PaymentGatewayException;
import com.foodordering.utils.JsonProvider;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpPayosGatewayTest {
    private static final String DOCUMENTED_KEY = "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
    private static final String DOCUMENTED_SIGNATURE = "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";
    private static final String DOCUMENTED_DATA = """
            {"orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678",
            "reference":"TF230204212323","transactionDateTime":"2023-02-04 18:25:00","currency":"VND",
            "paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00","desc":"Thành công",
            "counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"",
            "counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""}
            """;
    private PayosConfig config() { return new PayosConfig("test-client", "test-api", DOCUMENTED_KEY,
            "https://crave.example/orders/{orderId}?payos=return", "https://crave.example/orders/{orderId}?payos=cancel"); }

    @Test void matchesOfficialPayosSignatureVectorAndRejectsTampering() throws Exception {
        ObjectNode data = (ObjectNode) JsonProvider.objectMapper().readTree(DOCUMENTED_DATA);
        assertEquals(DOCUMENTED_SIGNATURE, HttpPayosGateway.signature(data, DOCUMENTED_KEY));
        assertTrue(HttpPayosGateway.verifySignature(data, DOCUMENTED_SIGNATURE, DOCUMENTED_KEY));
        data.put("amount", 3001);
        assertFalse(HttpPayosGateway.verifySignature(data, DOCUMENTED_SIGNATURE, DOCUMENTED_KEY));
        assertFalse(HttpPayosGateway.verifySignature(data, "invalid", DOCUMENTED_KEY));
    }
    @Test void signedWebhookValidatedAndAlteredOrMissingSignatureRejected() throws Exception {
        ObjectNode webhook = JsonProvider.objectMapper().createObjectNode().put("code", "00").put("success", true);
        webhook.set("data", JsonProvider.objectMapper().readTree(DOCUMENTED_DATA));
        webhook.put("signature", DOCUMENTED_SIGNATURE);
        HttpPayosGateway gateway = new HttpPayosGateway(config());
        assertEquals(123, gateway.verifyWebhook(webhook).orderCode());
        webhook.put("signature", "0".repeat(64));
        assertThrows(PaymentGatewayException.class, () -> gateway.verifyWebhook(webhook));
        webhook.remove("signature");
        assertThrows(PaymentGatewayException.class, () -> gateway.verifyWebhook(webhook));
    }
    @Test void postsSignedServerPayloadAndValidatesGatewayReply() throws Exception {
        AtomicReference<JsonNode> received = new AtomicReference<>();
        AtomicReference<String> credentials = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/payment-requests", exchange -> {
            received.set(JsonProvider.objectMapper().readTree(exchange.getRequestBody()));
            credentials.set(exchange.getRequestHeaders().getFirst("x-client-id") + ":" + exchange.getRequestHeaders().getFirst("x-api-key"));
            ObjectNode data = JsonProvider.objectMapper().createObjectNode().put("orderCode", 42).put("amount", 65000)
                    .put("paymentLinkId", "link01").put("checkoutUrl", "https://pay.payos.vn/web/link01").put("status", "PENDING");
            ObjectNode result = JsonProvider.objectMapper().createObjectNode().put("code", "00");
            result.set("data", data);
            result.put("signature", HttpPayosGateway.signature(data, DOCUMENTED_KEY));
            byte[] body = result.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            HttpPayosGateway gateway = new HttpPayosGateway(config(), HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            var link = gateway.createOrGet(42, 65000, "ABCDEF1234", false);
            assertEquals("https://pay.payos.vn/web/link01", link.checkoutUrl());
            assertEquals("test-client:test-api", credentials.get());
            ObjectNode request = (ObjectNode) received.get();
            assertEquals("CRAVE1234", request.path("description").asText());
            assertEquals("https://crave.example/orders/ABCDEF1234?payos=return", request.path("returnUrl").asText());
            String signature = request.remove("signature").asText();
            assertTrue(HttpPayosGateway.verifySignature(request, signature, DOCUMENTED_KEY));
        } finally { server.stop(0); }
    }
    @Test void missingCredentialsFailClosedAndCallbacksRequireAbsoluteHttpUrls() {
        assertThrows(PaymentGatewayException.class, () -> new PayosConfig("", "", "", "", "").requireConfigured());
        assertThrows(PaymentGatewayException.class, () -> new PayosConfig("client", "api", "key", "javascript:alert(1)", "https://example.com").requireConfigured());
        assertDoesNotThrow(() -> new PayosConfig("client", "api", "key", "http://example.com", "https://example.com").requireConfigured());
        assertDoesNotThrow(() -> new PayosConfig("client", "api", "key", "http://localhost:8080/crave/orders/{orderId}", "http://localhost:8080/crave/orders/{orderId}").requireConfigured());
    }
    @Test void upstreamRateLimitReturnsActionableErrorWithoutAutomaticRetry() throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/payment-requests", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "8");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpPayosGateway gateway = new HttpPayosGateway(config(), HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            PaymentGatewayException error = assertThrows(PaymentGatewayException.class, () -> gateway.createOrGet(42, 65000, "DH01", false));
            assertEquals("PAYOS_RATE_LIMITED", error.getErrorCode());
            assertEquals(503, error.getStatusCode());
            assertEquals(8, error.getRetryAfterSeconds());
            assertEquals(1, requests.get());
        } finally { server.stop(0); }
    }
}
