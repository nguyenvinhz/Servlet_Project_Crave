package com.foodordering.config;

import com.foodordering.exception.AccountException;
import com.foodordering.mail.SmtpVerificationEmailSender;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailConfigTest {
    private Map<String, String> gmail() {
        return new HashMap<>(Map.of("EMAIL_SMTP_USERNAME", "fixture@example.com", "EMAIL_FROM", "fixture@example.com",
                "EMAIL_SMTP_PASSWORD", "fixture-password", "EMAIL_OTP_SECRET", "fixture-secret-at-least-32-characters"));
    }

    @Test void defaultsRequireEncryptedAuthenticatedGmail() {
        var config = EmailConfig.smtp(gmail());
        assertEquals("smtp.gmail.com", config.host());
        assertEquals(587, config.port());
        assertTrue(config.startTls());
        assertFalse(config.toString().contains("fixture-password"));
    }

    @Test void rejectsPlaintextRemoteConnectionsAndInvalidHeaderValues() {
        for (Map<String, String> override : List.of(Map.of("EMAIL_SMTP_STARTTLS", "false"), Map.of("EMAIL_SMTP_PORT", "465"),
                Map.of("EMAIL_FROM", "fixture@example.com\r\nBcc: other@example.com"), Map.of("EMAIL_SMTP_PASSWORD", "fixture\npassword"),
                Map.of("EMAIL_SMTP_HOST", "bad host"), Map.of("EMAIL_SMTP_STARTTLS", "maybe"), Map.of("EMAIL_SMTP_PORT", "70000"))) {
            var values = gmail(); values.putAll(override);
            var failure = assertThrows(AccountException.class, () -> EmailConfig.smtp(values));
            assertEquals(503, failure.getStatusCode());
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("fixture"));
        }
    }

    @Test void plaintextIsAllowedOnlyForLoopbackFixtures() {
        var values = gmail();
        values.put("EMAIL_SMTP_HOST", "127.0.0.1"); values.put("EMAIL_SMTP_PORT", "2525");
        values.put("EMAIL_SMTP_STARTTLS", "false"); values.put("EMAIL_SMTP_USERNAME", ""); values.put("EMAIL_SMTP_PASSWORD", "");
        assertFalse(EmailConfig.smtp(values).startTls());
        values.put("EMAIL_SMTP_HOST", "smtp.example.com");
        assertThrows(AccountException.class, () -> EmailConfig.smtp(values));
    }

    @Test void otpPepperIsRequiredAndNeverIncludedInErrors() {
        assertEquals(gmail().get("EMAIL_OTP_SECRET"), EmailConfig.otpSecret(gmail()));
        for (String secret : new String[]{"", "short", "a".repeat(31), "a".repeat(513), "a".repeat(32) + "\n"}) {
            var failure = assertThrows(AccountException.class, () -> EmailConfig.otpSecret(Map.of("EMAIL_OTP_SECRET", secret)));
            assertEquals(503, failure.getStatusCode());
            assertFalse(failure.getMessage().contains("short"));
        }
    }

    @Test void senderConstructionIsLazyAndRejectsMalformedMessagesWithoutNetwork() {
        var sender = assertDoesNotThrow(SmtpVerificationEmailSender::new);
        for (String email : new String[]{"not-an-email", "fixture@example.com\r\nBcc: other@example.com"}) {
            var failure = assertThrows(AccountException.class, () -> sender.sendCode(email, "123456", "REGISTER"));
            assertEquals(503, failure.getStatusCode());
            assertNull(failure.getCause());
        }
        assertThrows(AccountException.class, () -> sender.sendCode("fixture@example.com", "not-a-code", "REGISTER"));
        assertThrows(AccountException.class, () -> sender.sendCode("fixture@example.com", "123456", "unknown"));
    }
}
