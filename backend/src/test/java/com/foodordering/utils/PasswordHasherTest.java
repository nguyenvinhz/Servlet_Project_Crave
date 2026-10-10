package com.foodordering.utils;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void hashingRejectsNulAndUnpairedSurrogates() {
        for (String invalid : List.of("Password#123" + '\0', "Pass" + '\0' + "word#123",
                "Password" + (char) 0xD800 + "42", "Password" + (char) 0xDC00 + "42",
                "Password#123" + (char) 0xD800)) {
            assertThrows(IllegalArgumentException.class, () -> hasher.hash(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
    }

    @Test
    void verificationRejectsPreviouslyEquivalentMalformedPasswords() {
        String questionMarkHash = hasher.hash("Password?42");
        assertFalse(hasher.verify("Password" + (char) 0xD800 + "42", questionMarkHash));
        assertFalse(hasher.verify("Password" + (char) 0xD801 + "42", questionMarkHash));
        assertFalse(hasher.verify("Password" + (char) 0xDC00 + "42", questionMarkHash));
        assertTrue(hasher.verify("Password?42", questionMarkHash));

        String plainHash = hasher.hash("Password#123");
        assertFalse(hasher.verify("Password#123" + '\0', plainHash));
        assertFalse(hasher.verify("Password#123" + '\0' + '\0', plainHash));
        assertTrue(hasher.verify("Password#123", plainHash));
    }

    @Test
    void validVietnameseAndEmojiPasswordsKeepTheirExactTextAndHashFormat() {
        String password = "MậtKhẩu#🔒123";
        String encoded = hasher.hash(password);

        assertTrue(encoded.startsWith("pbkdf2_sha256$600000$"));
        assertTrue(hasher.verify(password, encoded));
        assertFalse(hasher.verify("MậtKhẩu#🔑123", encoded));
        assertFalse(hasher.verify("MatKhau#🔒123", encoded));
    }

    @Test
    void invalidTextStillPerformsOneSafeDerivationAndCannotAuthenticateDummyKey() {
        AtomicInteger derivations = new AtomicInteger();
        PasswordHasher instrumented = new PasswordHasher((password, salt) -> {
            derivations.incrementAndGet();
            assertTrue(PasswordHasher.isValidPasswordText(password));
            return new byte[32];
        });
        String dummyHash = "pbkdf2_sha256$600000$"
                + Base64.getEncoder().encodeToString(new byte[16]) + "$"
                + Base64.getEncoder().encodeToString(new byte[32]);

        assertFalse(instrumented.verify("Password#123" + '\0', dummyHash));
        assertFalse(instrumented.verify("Password" + (char) 0xD800 + "42", null));
        assertEquals(2, derivations.get());
    }
}
