package com.foodordering.config;

import com.foodordering.exception.AccountException;
import com.foodordering.integration.PayosClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Server-only settings. Never expose credentials through a bean or toString(). */
public final class PayosConfig {
    private final String clientId;
    private final String apiKey;
    private final String checksumKey;
    private final String publicBaseUrl;

    private PayosConfig(String clientId, String apiKey, String checksumKey, String publicBaseUrl) {
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.checksumKey = checksumKey;
        this.publicBaseUrl = publicBaseUrl;
    }

    public static PayosConfig fromEnvironment() {
        Map<String, String> local = loadLocalSettings();
        return new PayosConfig(setting("PAYOS_CLIENT_ID", "", local),
                setting("PAYOS_API_KEY", "", local), setting("PAYOS_CHECKSUM_KEY", "", local),
                validateBaseUrl(setting("PAYOS_PUBLIC_BASE_URL", "http://localhost:8081/crave", local)));
    }

    public boolean configured() {
        return !clientId.isBlank() && !apiKey.isBlank() && !checksumKey.isBlank();
    }

    public PayosClient client() {
        if (!configured()) {
            throw new AccountException("PAYOS_NOT_CONFIGURED", "Thanh toán QR chưa được cấu hình.", 503);
        }
        return new PayosClient(clientId, apiKey, checksumKey);
    }

    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    static String validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString().replaceAll("/+$", "");
        } catch (IllegalArgumentException invalid) {
            throw new AccountException("PAYOS_CONFIGURATION_ERROR",
                    "PAYOS_PUBLIC_BASE_URL phải là URL đầy đủ của ứng dụng, gồm context path.", 503);
        }
    }

    private static String setting(String name, String fallback, Map<String, String> local) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = System.getProperty(name);
        if (value == null || value.isBlank()) value = local.get(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Map<String, String> loadLocalSettings() {
        Map<String, String> result = new HashMap<>();
        for (String candidate : new String[]{".env", "../.env", "../../.env"}) {
            Path path = Path.of(candidate);
            if (!Files.isRegularFile(path)) continue;
            try {
                for (String line : Files.readAllLines(path)) {
                    int separator = line.indexOf('=');
                    if (separator <= 0 || line.stripLeading().startsWith("#")) continue;
                    String name = line.substring(0, separator).trim();
                    if (!name.startsWith("PAYOS_")) continue;
                    String value = line.substring(separator + 1).trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    result.put(name, value);
                }
            } catch (IOException unreadable) {
                throw new AccountException("PAYOS_CONFIGURATION_ERROR", "Không đọc được cấu hình thanh toán.", 503);
            }
            break;
        }
        return result;
    }
}
