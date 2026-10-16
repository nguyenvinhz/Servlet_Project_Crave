package com.foodordering.integration.mail;

/** Server-side delivery only. Verification codes never become API response data. */
@FunctionalInterface
public interface VerificationEmailSender {
    void sendCode(String email, String code, String purpose);
}
