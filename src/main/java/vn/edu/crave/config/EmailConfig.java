package vn.edu.crave.config;

import vn.edu.crave.exception.AccountException;
import jakarta.mail.internet.InternetAddress;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Lazy server-only mail configuration. No credentials are included in toString or logs. */
public final class EmailConfig {
    private EmailConfig() {}

    public static String otpSecret() {
        return otpSecret(settings());
    }

    public static String otpSecret(Map<String, String> settings) {
        String secret = settings.getOrDefault("EMAIL_OTP_SECRET", "");
        if (secret.length() < 32 || secret.length() > 512
                || secret.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw unavailable();
        }
        return secret;
    }

    public static SmtpSettings smtp() {
        return smtp(settings());
    }

    public static SmtpSettings smtp(Map<String, String> settings) {
        try {
            String host = settings.getOrDefault("EMAIL_SMTP_HOST", "smtp.gmail.com").trim();
            if (!host.matches("[a-zA-Z0-9.-]+|::1") || host.length() > 253) throw unavailable();
            int port = Integer.parseInt(settings.getOrDefault("EMAIL_SMTP_PORT", "587"));
            if (port < 1 || port > 65535) throw unavailable();
            String tlsValue = settings.getOrDefault("EMAIL_SMTP_STARTTLS", "true");
            if (!"true".equalsIgnoreCase(tlsValue) && !"false".equalsIgnoreCase(tlsValue)) throw unavailable();
            boolean tls = Boolean.parseBoolean(tlsValue);
            // A plaintext server is supported exclusively for the isolated local SMTP fixture.
            boolean loopback = host.equals("127.0.0.1") || host.equals("::1") || host.equalsIgnoreCase("localhost");
            if (!tls && !loopback) throw unavailable();
            if (host.equalsIgnoreCase("smtp.gmail.com") && (port != 587 || !tls)) throw unavailable();
            String username = settings.getOrDefault("EMAIL_SMTP_USERNAME", "").trim();
            String password = settings.getOrDefault("EMAIL_SMTP_PASSWORD", "");
            if ((!loopback || tls) && (username.isEmpty() || password.isEmpty())) throw unavailable();
            if (username.length() > 320 || password.length() > 512
                    || username.chars().anyMatch(c -> c < 33 || c > 126)
                    || password.chars().anyMatch(c -> c < 32 || c > 126)) throw unavailable();
            String from = settings.getOrDefault("EMAIL_FROM", username).trim();
            if (from.isEmpty() || from.length() > 320 || from.contains("\r") || from.contains("\n")) throw unavailable();
            InternetAddress address = new InternetAddress(from, true);
            address.validate();
            if (address.getPersonal() != null || !address.getAddress().equals(from)) throw unavailable();
            return new SmtpSettings(host, port, username, password, from, tls);
        } catch (Exception invalid) {
            throw unavailable();
        }
    }

    private static Map<String, String> settings() {
        Map<String, String> result = new HashMap<>();
        for (String candidate : new String[]{".env", "../.env", "../../.env"}) {
            Path path = Path.of(candidate);
            if (!Files.isRegularFile(path)) continue;
            try {
                for (String line : Files.readAllLines(path)) {
                    int separator = line.indexOf('=');
                    if (separator <= 0 || line.stripLeading().startsWith("#")) continue;
                    String name = line.substring(0, separator).trim();
                    if (!name.startsWith("EMAIL_")) continue;
                    String value = line.substring(separator + 1).trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
                    result.put(name, value);
                }
            } catch (IOException unreadable) {
                throw unavailable();
            }
            break;
        }
        for (String name : new String[]{"EMAIL_SMTP_HOST", "EMAIL_SMTP_PORT", "EMAIL_SMTP_USERNAME",
                "EMAIL_SMTP_PASSWORD", "EMAIL_FROM", "EMAIL_SMTP_STARTTLS", "EMAIL_OTP_SECRET"}) {
            String value = System.getenv(name);
            if (value == null) value = System.getProperty(name);
            if (value != null) result.put(name, value);
        }
        return result;
    }

    private static AccountException unavailable() {
        return new AccountException("EMAIL_UNAVAILABLE", "Dịch vụ gửi mã xác thực tạm thời không khả dụng.", 503);
    }

    public static final class SmtpSettings {
        private final String host, username, password, from;
        private final int port;
        private final boolean startTls;

        private SmtpSettings(String host, int port, String username, String password, String from, boolean startTls) {
            this.host = host; this.port = port; this.username = username; this.password = password;
            this.from = from; this.startTls = startTls;
        }

        public String host() { return host; }
        public int port() { return port; }
        public String username() { return username; }
        public String password() { return password; }
        public String from() { return from; }
        public boolean startTls() { return startTls; }
    }
}
