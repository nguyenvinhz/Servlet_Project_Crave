package vn.edu.crave.integration;

/** Server-side delivery only. Verification codes never become API response data. */
@FunctionalInterface
public interface VerificationEmailSender {
    void sendCode(String email, String code, String purpose);
}
