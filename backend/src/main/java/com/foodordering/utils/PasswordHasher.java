package com.foodordering.utils;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.BiFunction;

/** Salted PBKDF2 hashes; unsupported or malformed database values never authenticate. */
public final class PasswordHasher {

    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final String FORMAT = "pbkdf2_sha256";
    private final SecureRandom random = new SecureRandom();
    private final BiFunction<String, byte[], byte[]> keyDerivation;

    public PasswordHasher() {
        this(PasswordHasher::derive);
    }

    PasswordHasher(BiFunction<String, byte[], byte[]> keyDerivation) {
        this.keyDerivation = keyDerivation;
    }

    public String hash(String password) {
        if (!isValidPasswordText(password)) {
            throw new IllegalArgumentException("Password contains NUL or malformed Unicode.");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] key = keyDerivation.apply(password, salt);
        return FORMAT + "$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(key);
    }

    public boolean verify(String password, String encoded) {
        if (password == null) {
            return false;
        }
        boolean validPassword = isValidPasswordText(password);
        // Unknown accounts and unsupported stored hashes consume the same PBKDF2 work as a real login.
        byte[] salt = new byte[SALT_BYTES];
        byte[] expected = new byte[KEY_BITS / 8];
        boolean supported = false;
        if (encoded != null && encoded.length() <= 255) {
            String[] fields = encoded.split("\\$", -1);
            if (fields.length == 4 && FORMAT.equals(fields[0]) && Integer.toString(ITERATIONS).equals(fields[1])) {
                try {
                    byte[] decodedSalt = Base64.getDecoder().decode(fields[2]);
                    byte[] decodedKey = Base64.getDecoder().decode(fields[3]);
                    if (decodedSalt.length == SALT_BYTES && decodedKey.length == KEY_BITS / 8) {
                        salt = decodedSalt;
                        expected = decodedKey;
                        supported = true;
                    }
                } catch (IllegalArgumentException ignored) {
                    // Continue through the dummy derivation for malformed Base64.
                }
            }
        }
        // Still do one derivation for invalid text, without passing lossy input to the KDF.
        byte[] actual = keyDerivation.apply(validPassword ? password : "invalid-password", salt);
        try {
            boolean matches = MessageDigest.isEqual(expected, actual);
            return validPassword && supported && matches;
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    /** Prevents UTF-8 replacement and HMAC zero-padding from making distinct passwords equivalent. */
    public static boolean isValidPasswordText(String password) {
        if (password == null) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            char character = password.charAt(i);
            if (character == '\0') {
                return false;
            }
            if (Character.isHighSurrogate(character)) {
                if (++i >= password.length() || !Character.isLowSurrogate(password.charAt(i))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    private static byte[] derive(String password, byte[] salt) {
        char[] characters = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(characters, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable.", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(characters, '\0');
        }
    }
}
