package com.foodordering.config;

import com.foodordering.exception.AccountException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayosConfigTest {
    @Test
    void baseUrlPreservesConfiguredPortAndContextAndRemovesTrailingSlash() {
        assertEquals("http://localhost:8081/crave", PayosConfig.validateBaseUrl("http://localhost:8081/crave/"));
        assertEquals("https://example.com/app", PayosConfig.validateBaseUrl("https://example.com/app"));
    }

    @Test
    void unsafeOrIncompleteCallbackBasesAreRejectedWithoutEchoingValue() {
        for (String value : new String[]{"/crave", "javascript:alert(1)", "https://user:secret@example.com/crave",
                "https://example.com/crave?secret=value", "https://example.com/crave#fragment"}) {
            AccountException error = assertThrows(AccountException.class, () -> PayosConfig.validateBaseUrl(value));
            assertEquals(503, error.getStatusCode());
            assertFalse(error.getMessage().contains("secret"));
        }
    }
}
