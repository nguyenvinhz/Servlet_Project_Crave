package com.foodordering.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodordering.config.PayosConfig;
import com.foodordering.exception.PaymentGatewayException;
import com.foodordering.utils.JsonProvider;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** payOS payment-requests API; request, response and webhook signatures follow the merchant docs. */
public class HttpPayosGateway implements PayosGateway {
    private static final Logger LOGGER = Logger.getLogger(HttpPayosGateway.class.getName());
    private final PayosConfig config;
    private final HttpClient client;
    private final URI apiBase;

    public HttpPayosGateway(PayosConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(),
                URI.create("https://api-merchant.payos.vn"));
    }
    HttpPayosGateway(PayosConfig config, HttpClient client, URI apiBase) {
        this.config = config;
        this.client = client;
        this.apiBase = apiBase;
    }
    @Override public void requireConfigured() { config.requireConfigured(); }

    @Override public PaymentLink createOrGet(long orderCode, long amount, String orderId, boolean retry) {
        requireConfigured();
        if (retry) {
            JsonNode existing = lookup(orderCode);
            if ("00".equals(existing.path("code").asText())) return existingLink(existing, orderCode, amount);
            // A retry always uses the already reserved code, including after an interrupted create response.
        }
        ObjectNode body = JsonProvider.objectMapper().createObjectNode();
        body.put("orderCode", orderCode).put("amount", amount).put("description", "CRAVE" + orderId.substring(Math.max(0, orderId.length() - 4)))
                .put("returnUrl", config.returnUrl(orderId)).put("cancelUrl", config.cancelUrl(orderId));
        body.put("signature", signature(body, config.checksumKey()));
        JsonNode response = call("POST", "/v2/payment-requests", body);
        if (!"00".equals(response.path("code").asText())) return existingLink(lookup(orderCode), orderCode, amount);
        JsonNode data = successfulData(response, true);
        verifyIdentity(data, orderCode, amount);
        return link(data);
    }

    private PaymentLink existingLink(JsonNode response, long orderCode, long amount) {
        JsonNode data = successfulData(response, true);
        verifyIdentity(data, orderCode, amount);
        String id = requiredText(data, "id");
        if (!id.matches("[A-Za-z0-9-]{1,100}")) throw unavailable();
        return new PaymentLink(orderCode, amount, id, "https://pay.payos.vn/web/" + id,
                requiredText(data, "status"), number(data, "amountPaid"));
    }
    private PaymentLink link(JsonNode data) {
        String url = requiredText(data, "checkoutUrl");
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException invalid) { throw unavailable(); }
        if (!"https".equals(uri.getScheme()) || !"pay.payos.vn".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1) throw unavailable();
        return new PaymentLink(number(data, "orderCode"), number(data, "amount"),
                requiredText(data, "paymentLinkId"), url, requiredText(data, "status"));
    }
    @Override public PaymentState getPayment(long orderCode) {
        requireConfigured();
        JsonNode data = successfulData(lookup(orderCode), true);
        if (number(data, "orderCode") != orderCode) throw unavailable();
        JsonNode transactions = data.path("transactions");
        String reference = transactions.isArray() && !transactions.isEmpty()
                ? transactions.get(0).path("reference").asText("") : "";
        return new PaymentState(orderCode, number(data, "amount"), number(data, "amountPaid"),
                requiredText(data, "id"), requiredText(data, "status"), reference);
    }
    private JsonNode lookup(long code) { return call("GET", "/v2/payment-requests/" + code, null); }

    @Override public WebhookPayment verifyWebhook(JsonNode webhook) {
        requireConfigured();
        if (webhook == null || !webhook.isObject() || !verifySignature(webhook.path("data"),
                webhook.path("signature").asText(""), config.checksumKey())) {
            throw new PaymentGatewayException("PAYOS_INVALID_SIGNATURE", "Chữ ký thanh toán không hợp lệ.", 400);
        }
        JsonNode data = webhook.path("data");
        if (!"VND".equals(requiredText(data, "currency"))) {
            throw new PaymentGatewayException("PAYOS_INVALID_PAYMENT", "Loại tiền thanh toán không hợp lệ.", 400);
        }
        boolean successful = "00".equals(webhook.path("code").asText())
                && webhook.path("success").isBoolean() && webhook.path("success").asBoolean()
                && "00".equals(data.path("code").asText());
        return new WebhookPayment(number(data, "orderCode"), number(data, "amount"),
                requiredText(data, "paymentLinkId"), requiredText(data, "reference"), successful);
    }

    private JsonNode call(String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(apiBase.resolve(path)).timeout(Duration.ofSeconds(15))
                    .header("x-client-id", config.clientId()).header("x-api-key", config.apiKey())
                    .header("Accept", "application/json");
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 429) {
                LOGGER.warning("PayOS upstream rate limit: method=" + method + ", httpStatus=429");
                int retrySeconds = 5;
                try {
                    retrySeconds = Math.max(1, Math.min(60, Integer.parseInt(response.headers().firstValue("Retry-After").orElse("5"))));
                } catch (NumberFormatException ignored) { }
                throw new PaymentGatewayException("PAYOS_RATE_LIMITED", "PayOS đang giới hạn lượt yêu cầu. Đơn hàng vẫn được lưu; vui lòng đợi vài giây rồi thử lại.", 503, retrySeconds);
            }
            if (response.statusCode() >= 500 || response.statusCode() == 401) {
                LOGGER.warning("PayOS upstream request rejected: method=" + method + ", httpStatus=" + response.statusCode());
                throw unavailable();
            }
            JsonNode json = JsonProvider.objectMapper().readTree(response.body());
            if (json == null || !json.isObject()) throw unavailable();
            if (!"00".equals(json.path("code").asText())) {
                String providerCode = json.path("code").asText("");
                LOGGER.warning("PayOS upstream response: method=" + method + ", httpStatus=" + response.statusCode()
                        + ", providerCode=" + (providerCode.matches("[0-9]{1,6}") ? providerCode : "unrecognized"));
            }
            return json;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warning("PayOS transport interrupted: exceptionClass=" + exception.getClass().getSimpleName());
            throw unavailable();
        } catch (java.io.IOException | IllegalArgumentException exception) {
            LOGGER.warning("PayOS transport failed: exceptionClass=" + exception.getClass().getSimpleName());
            throw unavailable();
        }
    }
    private JsonNode successfulData(JsonNode response, boolean signed) {
        if (!"00".equals(response.path("code").asText()) || !response.path("data").isObject()) throw unavailable();
        if (signed && !verifySignature(response.path("data"), response.path("signature").asText(""), config.checksumKey())) {
            LOGGER.warning("PayOS upstream response rejected: signatureValid=false");
            throw unavailable();
        }
        return response.path("data");
    }
    private static void verifyIdentity(JsonNode data, long code, long amount) {
        if (number(data, "orderCode") != code || number(data, "amount") != amount) throw unavailable();
    }
    private static long number(JsonNode data, String field) {
        JsonNode value = data.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) throw invalid();
        return value.longValue();
    }
    private static String requiredText(JsonNode data, String field) {
        JsonNode value = data.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 500) throw invalid();
        return value.asText();
    }
    private static PaymentGatewayException invalid() { return new PaymentGatewayException("PAYOS_INVALID_PAYMENT", "Dữ liệu thanh toán PayOS không hợp lệ.", 400); }
    private static PaymentGatewayException unavailable() { return new PaymentGatewayException("PAYOS_UNAVAILABLE", "Chưa kết nối được PayOS. Đơn hàng đã được lưu; bạn có thể thanh toán lại trong chi tiết đơn hàng.", 502); }

    public static String signature(JsonNode data, String key) {
        if (data == null || !data.isObject()) throw invalid();
        TreeMap<String, String> fields = new TreeMap<>();
        data.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            String text = value.isNull() ? "" : value.isContainerNode() ? sortedArray(value).toString() : value.asText();
            fields.put(entry.getKey(), "null".equals(text) || "undefined".equals(text) ? "" : text);
        });
        String input = fields.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) { throw new IllegalStateException("HMAC unavailable", exception); }
    }
    private static JsonNode sortedArray(JsonNode value) {
        if (!value.isArray()) return value;
        var array = JsonProvider.objectMapper().createArrayNode();
        value.forEach(item -> {
            if (!item.isObject()) { array.add(item); return; }
            ObjectNode sorted = JsonProvider.objectMapper().createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            item.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach(sorted::set);
            array.add(sorted);
        });
        return array;
    }
    public static boolean verifySignature(JsonNode data, String expected, String key) {
        if (data == null || !data.isObject() || expected == null || !expected.matches("[0-9a-fA-F]{64}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(signature(data, key)), HexFormat.of().parseHex(expected));
    }
}
