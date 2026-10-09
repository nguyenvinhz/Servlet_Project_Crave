package com.foodordering.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesUseIndependentSaltsAndVerifyOnlyTheOriginalPassword() {
        String first = hasher.hash("MậtKhẩu#123");
        String second = hasher.hash("MậtKhẩu#123");

        assertNotEquals(first, second);
        assertTrue(first.startsWith("pbkdf2_sha256$600000$"));
        assertTrue(hasher.verify("MậtKhẩu#123", first));
        assertFalse(hasher.verify("WrongPassword", first));
        assertFalse(first.contains("MậtKhẩu#123"));
    }

    @Test
    void refusesSeedPlaceholdersMalformedValuesAndUnsupportedWorkFactors() {
        assertFalse(hasher.verify("customer123", "hashed_customer123"));
        assertFalse(hasher.verify("password123", "password123"));
        assertFalse(hasher.verify("password123", "pbkdf2_sha256$600000$???$???"));
        assertFalse(hasher.verify("password123", "pbkdf2_sha256$999999999$AA==$AA=="));
        assertFalse(hasher.verify("password123", null));
        assertFalse(hasher.verify(null, "hashed_customer123"));
    }

    @Test
    void missingAndUnsupportedHashesAlwaysPerformOneDerivationWithoutAuthenticatingDummyKey() {
        AtomicInteger derivations = new AtomicInteger();
        PasswordHasher instrumented = new PasswordHasher((password, salt) -> {
            derivations.incrementAndGet();
            assertEquals("password123", password);
            assertEquals(16, salt.length);
            return new byte[32];
        });
        String[] unsupported = {null, "hashed_customer123", "password123", "x".repeat(256),
                "pbkdf2_sha256$600000$???$???", "pbkdf2_sha256$999999999$AA==$AA==",
                "pbkdf2_sha256$600000$AA==$AA=="};

        for (String encoded : unsupported) {
            assertFalse(instrumented.verify("password123", encoded));
        }
        assertEquals(unsupported.length, derivations.get());
    }
}
