package com.foodordering.config;

import com.foodordering.exception.PaymentGatewayException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Gateway credentials stay on the server, loaded from environment or the ignored local .env. */
public final class PayosConfig {
    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final String returnUrl;
    private final String cancelUrl;

    public PayosConfig(String clientId, String apiKey, String checksumKey, String returnUrl, String cancelUrl) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.checksumKey = checksumKey;
        this.returnUrl = returnUrl;
        this.cancelUrl = cancelUrl;
    }

    public static PayosConfig fromEnvironment() {
        loadDotEnv();
        String base = env("PAYOS_PUBLIC_BASE_URL").replaceAll("/+$", "");
        return new PayosConfig(env("PAYOS_CLIENT_ID"), env("PAYOS_API_KEY"), env("PAYOS_CHECKSUM_KEY"),
                fallback(env("PAYOS_RETURN_URL"), base.isEmpty() ? "" : base + "/orders/{orderId}?payos=return"),
                fallback(env("PAYOS_CANCEL_URL"), base.isEmpty() ? "" : base + "/orders/{orderId}?payos=cancel"));
    }

    public void requireConfigured() {
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()
                || checksumKey == null || checksumKey.isBlank() || returnUrl == null || returnUrl.isBlank()
                || cancelUrl == null || cancelUrl.isBlank()) {
            throw new PaymentGatewayException("PAYOS_NOT_CONFIGURED", "Chuyển khoản PayOS chưa được cấu hình. Vui lòng chọn tiền mặt hoặc thử lại sau.", 503);
        }
        validateUrl(returnUrl.replace("{orderId}", "order"));
        validateUrl(cancelUrl.replace("{orderId}", "order"));
    }

    private static void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new PaymentGatewayException("PAYOS_NOT_CONFIGURED", "Địa chỉ trả về của PayOS chưa được cấu hình hợp lệ.", 503);
        }
    }

    public String clientId() { return clientId; }
    public String apiKey() { return apiKey; }
    public String checksumKey() { return checksumKey; }
    public String returnUrl(String orderId) { return returnUrl.replace("{orderId}", orderId); }
    public String cancelUrl(String orderId) { return cancelUrl.replace("{orderId}", orderId); }
    private static String fallback(String value, String other) { return value.isEmpty() ? other : value; }
    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name, "");
        return value.trim();
    }
    private static void loadDotEnv() {
        for (String candidate : new String[]{".env", "../.env", "../../.env"}) {
            Path path = Path.of(candidate);
            if (!Files.isRegularFile(path)) continue;
            try {
                for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    int separator = line.indexOf('=');
                    if (line.startsWith("#") || separator < 1) continue;
                    String key = line.substring(0, separator).trim();
                    if (!key.startsWith("PAYOS_") || System.getProperty(key) != null) continue;
                    System.setProperty(key, line.substring(separator + 1).trim());
                }
            } catch (java.io.IOException ignored) { }
            break;
        }
    }
}
