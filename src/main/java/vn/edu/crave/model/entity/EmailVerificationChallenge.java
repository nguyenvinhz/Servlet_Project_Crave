package vn.edu.crave.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** One locked challenge/rate-limit row per normalized email and purpose. Never stores a plaintext code. */
@Entity
@Table(name = "email_verification_challenges")
public class EmailVerificationChallenge {
    @Id
    @Column(name = "challenge_id", length = 64, nullable = false)
    private String id;
    @Column(name = "email", length = 100, nullable = false)
    private String email;
    @Column(name = "purpose", length = 24, nullable = false)
    private String purpose;
    @Column(name = "user_id", length = 10)
    private String userId;
    @Column(name = "account_binding", length = 64)
    private String accountBinding;
    @Column(name = "code_salt", length = 32, nullable = false)
    private String codeSalt;
    @Column(name = "code_digest", length = 64, nullable = false)
    private String codeDigest;
    @Column(name = "issued_at_ms", nullable = false)
    private long issuedAt;
    @Column(name = "expires_at_ms", nullable = false)
    private long expiresAt;
    @Column(name = "attempts", nullable = false)
    private int attempts;
    @Column(name = "consumed", nullable = false)
    private boolean consumed;
    @Column(name = "window_started_at_ms", nullable = false)
    private long windowStartedAt;
    @Column(name = "window_send_count", nullable = false)
    private int windowSendCount;
    @Column(name = "send_history", length = 100, nullable = false)
    private String sendHistory = "";

    protected EmailVerificationChallenge() { }

    public EmailVerificationChallenge(String id, String email, String purpose) {
        this.id = id;
        this.email = email;
        this.purpose = purpose;
    }

    public void issue(String userId, String accountBinding, String salt, String digest,
                      long now, long expiresAt, long windowDuration) {
        List<Long> recent = recentSends(now, windowDuration);
        windowStartedAt = recent.isEmpty() ? now : recent.get(0);
        windowSendCount = recent.size() + 1;
        sendHistory = recent.stream().map(String::valueOf).collect(Collectors.joining(","));
        sendHistory = sendHistory.isEmpty() ? Long.toString(now) : sendHistory + "," + now;
        this.userId = userId;
        this.accountBinding = accountBinding;
        this.codeSalt = salt;
        this.codeDigest = digest;
        this.issuedAt = now;
        this.expiresAt = expiresAt;
        this.attempts = 0;
        this.consumed = false;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPurpose() { return purpose; }
    public String getUserId() { return userId; }
    public String getAccountBinding() { return accountBinding; }
    public String getCodeSalt() { return codeSalt; }
    public String getCodeDigest() { return codeDigest; }
    public long getIssuedAt() { return issuedAt; }
    public long getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public boolean isConsumed() { return consumed; }
    public long getWindowStartedAt() { return windowStartedAt; }
    public int getWindowSendCount() { return windowSendCount; }
    public int recentSendCount(long now, long duration) { return recentSends(now, duration).size(); }
    public void recordFailure() { attempts++; }
    public void consume() { consumed = true; }

    private List<Long> recentSends(long now, long duration) {
        if (sendHistory == null || sendHistory.isEmpty()) return List.of();
        return Arrays.stream(sendHistory.split(",")).map(Long::parseLong)
                .filter(sentAt -> sentAt > now - duration).toList();
    }

    @Override
    public String toString() { return "EmailVerificationChallenge[verification data redacted]"; }
}
