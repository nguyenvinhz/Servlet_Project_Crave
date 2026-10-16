package com.foodordering.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {
    @TempDir Path directory;
    private final Map<String, String> previous = new HashMap<>();

    @AfterEach
    void restoreProperties() {
        previous.forEach((name, value) -> {
            if (value == null) System.clearProperty(name);
            else System.setProperty(name, value);
        });
    }

    @Test
    void databaseBootstrapPreservesQuotedMailPaymentAndJdbcSettings() throws Exception {
        // Initialize the production loader before isolating the fixture's property values.
        DatabaseConfig.properties();
        String[] names = {"EMAIL_SMTP_HOST", "EMAIL_SMTP_PASSWORD", "EMAIL_OTP_SECRET",
                "PAYOS_CHECKSUM_KEY", "PAYOS_PUBLIC_BASE_URL", "DB_URL", "DB_PASSWORD"};
        for (String name : names) isolate(name);
        Path dotenv = directory.resolve(".env");
        Files.writeString(dotenv, "\uFEFFDB_URL=\"jdbc:mysql://localhost:3307/fixture?useSSL=false\"\n"
                + "DB_PASSWORD='fixture#password=with symbols'\n"
                + "EMAIL_SMTP_HOST='smtp.gmail.com'\n"
                + "EMAIL_SMTP_PASSWORD=\"fixture app password\"\n"
                + "EMAIL_OTP_SECRET='fixture-otp-secret-with-at-least-32-characters'\n"
                + "PAYOS_CHECKSUM_KEY=\"fixture-checksum-key\"\n"
                + "PAYOS_PUBLIC_BASE_URL='https://fixture.example/crave'\n");

        DatabaseConfig.loadDotEnv(dotenv);

        assertEquals("jdbc:mysql://localhost:3307/fixture?useSSL=false", System.getProperty("DB_URL"));
        assertEquals("fixture#password=with symbols", System.getProperty("DB_PASSWORD"));
        assertEquals("smtp.gmail.com", System.getProperty("EMAIL_SMTP_HOST"));
        assertEquals("fixture app password", System.getProperty("EMAIL_SMTP_PASSWORD"));
        assertEquals("fixture-otp-secret-with-at-least-32-characters", System.getProperty("EMAIL_OTP_SECRET"));
        assertEquals("fixture-checksum-key", System.getProperty("PAYOS_CHECKSUM_KEY"));
        assertEquals("https://fixture.example/crave", System.getProperty("PAYOS_PUBLIC_BASE_URL"));
    }

    @Test
    void explicitJvmSettingsWinAndOnlyTheFirstAvailableDotEnvIsLoaded() throws Exception {
        DatabaseConfig.properties();
        isolate("PAYOS_CHECKSUM_KEY");
        isolate("EMAIL_FROM");
        System.setProperty("PAYOS_CHECKSUM_KEY", "jvm-fixture-key");
        Path first = directory.resolve("first.env");
        Path second = directory.resolve("second.env");
        Files.writeString(first, "# fixture comment\nPAYOS_CHECKSUM_KEY='local-fixture-key'\n"
                + "EMAIL_FROM=first@example.com\n");
        Files.writeString(second, "EMAIL_FROM=second@example.com\n");

        DatabaseConfig.loadDotEnv(directory.resolve("missing.env"), directory, first, second);

        assertEquals("jvm-fixture-key", System.getProperty("PAYOS_CHECKSUM_KEY"));
        assertEquals("first@example.com", System.getProperty("EMAIL_FROM"));
    }

    private void isolate(String name) {
        previous.put(name, System.getProperty(name));
        System.clearProperty(name);
    }
}
