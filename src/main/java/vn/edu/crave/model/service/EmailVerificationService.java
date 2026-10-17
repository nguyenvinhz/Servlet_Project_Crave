package vn.edu.crave.model.service;

import vn.edu.crave.config.EmailConfig;
import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.form.RegisterRequest;
import vn.edu.crave.model.entity.Customer;
import vn.edu.crave.model.entity.EmailVerificationChallenge;
import vn.edu.crave.model.entity.Employee;
import vn.edu.crave.model.entity.User;
import vn.edu.crave.model.enums.CustomerStatus;
import vn.edu.crave.model.enums.EmployeeStatus;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.integration.SmtpVerificationEmailSender;
import vn.edu.crave.integration.VerificationEmailSender;
import vn.edu.crave.model.dao.AccountDAO;
import vn.edu.crave.util.PasswordHasher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/** Persistent, single-use email challenges. OTPs and their account mutations share one transaction. */
public class EmailVerificationService {
    public static final int CODE_TTL_SECONDS = 600;
    public static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final long HOUR_MS = Duration.ofHours(1).toMillis();
    private static final String REGISTER = "REGISTER";
    private static final String RESET = "PASSWORD_RESET";
    private static final String CHANGE = "PASSWORD_CHANGE";

    private final AccountDAO accountDAO;
    private final AccountService accounts;
    private final PasswordHasher passwordHasher;
    private final VerificationEmailSender sender;
    private final Clock clock;
    private final Supplier<String> otpSecret;
    private final SecureRandom random;

    public EmailVerificationService() {
        this(new AccountDAO(), new PasswordHasher(), new SmtpVerificationEmailSender(),
                Clock.systemUTC());
    }

    public EmailVerificationService(AccountDAO accountDAO, VerificationEmailSender sender, Clock clock) {
        this(accountDAO, new PasswordHasher(), sender, clock);
    }

    public EmailVerificationService(AccountDAO accountDAO, PasswordHasher passwordHasher,
                                    VerificationEmailSender sender, Clock clock) {
        this(accountDAO, passwordHasher, sender, clock, EmailConfig::otpSecret, new SecureRandom());
    }

    public EmailVerificationService(AccountDAO accountDAO, PasswordHasher passwordHasher,
                                    VerificationEmailSender sender, Clock clock, String otpSecret) {
        this(accountDAO, passwordHasher, sender, clock, () -> otpSecret, new SecureRandom());
    }

    EmailVerificationService(AccountDAO accountDAO, PasswordHasher passwordHasher,
                             VerificationEmailSender sender, Clock clock,
                             Supplier<String> otpSecret, SecureRandom random) {
        this.accountDAO = Objects.requireNonNull(accountDAO);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.accounts = new AccountService(accountDAO, passwordHasher);
        this.sender = Objects.requireNonNull(sender);
        this.clock = Objects.requireNonNull(clock);
        this.otpSecret = Objects.requireNonNull(otpSecret);
        this.random = Objects.requireNonNull(random);
    }

    public void requestRegistrationCode(String email) {
        String normalized = validEmail(email);
        byte[] key = key();
        try {
            Delivery delivery = writeRetry(session -> {
                if (session.findByEmail(normalized, true).isPresent()) {
                    throw new AccountException("ACCOUNT_CONFLICT", "Email đã được sử dụng.", 409,
                            Map.of("email", "Email đã được sử dụng."));
                }
                return issue(session, normalized, REGISTER, null, key, true);
            });
            deliver(delivery);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public ProfileResponse register(RegisterRequest request, String verificationCode) {
        AccountService.Registration registration = accounts.prepareRegistration(request);
        byte[] key = key();
        try {
            VerificationResult<ProfileResponse> result = accountDAO.write(session -> {
                EmailVerificationChallenge challenge = challenge(session, registration.email(), REGISTER);
                AccountException failure = verify(challenge, verificationCode, null, key);
                if (failure != null) return VerificationResult.failure(failure);
                ProfileResponse profile = accounts.register(session, registration);
                challenge.consume();
                session.flush();
                return VerificationResult.success(profile);
            });
            return result.valueOrThrow();
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /** All valid email addresses receive the same acknowledgement and throttling, without account enumeration. */
    public void requestPasswordResetCode(String email) {
        String normalized = validEmail(email);
        byte[] key = key();
        try {
            Delivery delivery = writeRetry(session -> {
                User user = session.findByEmail(normalized, true).orElse(null);
                boolean eligible = active(user);
                return issue(session, normalized, RESET, eligible ? user : null, key, eligible);
            });
            if (delivery.send()) {
                try {
                    deliver(delivery);
                } catch (RuntimeException ignored) {
                    // A mail outage must not turn an otherwise generic reset acknowledgement into an account oracle.
                }
            }
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public void resetPassword(String email, String code, String password, String confirmPassword) {
        String normalized = validEmail(email);
        String passwordHash = newPasswordHash(password, confirmPassword);
        byte[] key = key();
        try {
            VerificationResult<Void> result = accountDAO.write(session -> {
                User user = session.findByEmail(normalized, true).orElse(null);
                EmailVerificationChallenge challenge = challenge(session, normalized, RESET);
                AccountException failure = verify(challenge, code, user, key);
                if (failure != null) return VerificationResult.failure(failure);
                user.setPasswordHash(passwordHash);
                user.incrementPasswordVersion();
                challenge.consume();
                session.flush();
                return VerificationResult.success(null);
            });
            result.valueOrThrow();
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public void requestPasswordChangeCode(String userId, String currentPassword) {
        AccountService.requireSessionId(userId);
        Map<String, String> errors = new LinkedHashMap<>();
        AccountService.validatePassword(currentPassword, "currentPassword", errors);
        AccountService.rejectInvalid(errors);
        byte[] key = key();
        try {
            Delivery delivery = writeRetry(session -> {
                User user = requireUser(session, userId);
                if (!passwordHasher.verify(currentPassword, user.getPasswordHash())) {
                    throw new AccountException("INVALID_CURRENT_PASSWORD", "Mật khẩu hiện tại không đúng.", 400,
                            Map.of("currentPassword", "Mật khẩu hiện tại không đúng."));
                }
                return issue(session, AccountService.normalizeEmail(user.getEmail()), CHANGE, user, key, true);
            });
            deliver(delivery);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    public void changePassword(String userId, String code, String password, String confirmPassword) {
        AccountService.requireSessionId(userId);
        String passwordHash = newPasswordHash(password, confirmPassword);
        byte[] key = key();
        try {
            VerificationResult<Void> result = accountDAO.write(session -> {
                User user = requireUser(session, userId);
                EmailVerificationChallenge challenge = challenge(session,
                        AccountService.normalizeEmail(user.getEmail()), CHANGE);
                AccountException failure = verify(challenge, code, user, key);
                if (failure != null) return VerificationResult.failure(failure);
                user.setPasswordHash(passwordHash);
                user.incrementPasswordVersion();
                challenge.consume();
                session.flush();
                return VerificationResult.success(null);
            });
            result.valueOrThrow();
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private Delivery issue(AccountDAO.Session session, String email, String purpose,
                           User user, byte[] key, boolean send) {
        long now = clock.millis();
        String id = challengeId(email, purpose);
        EmailVerificationChallenge challenge = session.findVerificationChallenge(id, true).orElse(null);
        boolean fresh = challenge == null;
        if (fresh) challenge = new EmailVerificationChallenge(id, email, purpose);
        if (!fresh && (now - challenge.getIssuedAt() < RESEND_COOLDOWN_SECONDS * 1000L
                || challenge.recentSendCount(now, HOUR_MS) >= MAX_SENDS_PER_HOUR)) {
            throw new AccountException("VERIFICATION_RATE_LIMIT", "Vui lòng chờ trước khi yêu cầu mã mới.", 429,
                    Map.of("email", "Bạn đã yêu cầu mã quá nhanh hoặc quá nhiều lần. Vui lòng thử lại sau."));
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        byte[] saltBytes = new byte[16];
        random.nextBytes(saltBytes);
        String salt = HexFormat.of().formatHex(saltBytes);
        String digest = codeDigest(key, id, salt, code);
        challenge.issue(user == null ? null : user.getId(), user == null ? null : binding(key, user),
                salt, digest, now, now + CODE_TTL_SECONDS * 1000L, HOUR_MS);
        if (fresh) session.persistVerificationChallenge(challenge);
        session.flush();
        return new Delivery(id, email, purpose, code, digest, send);
    }

    private EmailVerificationChallenge challenge(AccountDAO.Session session, String email, String purpose) {
        return session.findVerificationChallenge(challengeId(email, purpose), true).orElse(null);
    }

    /** Returning failures lets the repository commit the wrong-attempt counter before the public method throws. */
    private AccountException verify(EmailVerificationChallenge challenge, String code, User user, byte[] key) {
        if (challenge == null || challenge.isConsumed() || clock.millis() >= challenge.getExpiresAt()
                || challenge.getAttempts() >= MAX_ATTEMPTS) {
            return invalidCode();
        }
        if (!REGISTER.equals(challenge.getPurpose())
                && (!active(user) || !Objects.equals(challenge.getUserId(), user.getId())
                    || !Objects.equals(challenge.getEmail(), AccountService.normalizeEmail(user.getEmail()))
                    || !constantEquals(challenge.getAccountBinding(), binding(key, user)))) {
            challenge.consume();
            return invalidCode();
        }
        boolean validFormat = code != null && code.matches("[0-9]{6}");
        String actual = codeDigest(key, challenge.getId(), challenge.getCodeSalt(),
                validFormat ? code : "invalid-code");
        if (!validFormat || !constantEquals(challenge.getCodeDigest(), actual)) {
            challenge.recordFailure();
            return invalidCode();
        }
        return null;
    }

    private void deliver(Delivery delivery) {
        try {
            sender.sendCode(delivery.email(), delivery.code(), delivery.purpose());
        } catch (RuntimeException failure) {
            // Preserve rate counters, while making an undelivered challenge unusable. Never expose sender internals.
            writeRetry(session -> {
                EmailVerificationChallenge challenge = session.findVerificationChallenge(delivery.id(), true).orElse(null);
                if (challenge != null && constantEquals(challenge.getCodeDigest(), delivery.digest())) {
                    challenge.consume();
                    session.flush();
                }
                return null;
            });
            throw new AccountException("EMAIL_DELIVERY_FAILED", "Chưa thể gửi email xác thực. Vui lòng thử lại sau.", 503);
        }
    }

    private <T> T writeRetry(Function<AccountDAO.Session, T> action) {
        // The unique challenge key also serializes concurrent first requests, before there is a row to lock.
        for (int attempt = 0; ; attempt++) {
            try {
                return accountDAO.write(action);
            } catch (RuntimeException failure) {
                if (attempt >= 2 || !retryable(failure)) throw failure;
            }
        }
    }

    private static boolean retryable(RuntimeException failure) {
        if (failure instanceof AccountException account && "RESOURCE_CONFLICT".equals(account.getErrorCode())) return true;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (sql.getErrorCode() == 1062 || sql.getErrorCode() == 1205 || sql.getErrorCode() == 1213)) return true;
        }
        return false;
    }

    private String newPasswordHash(String password, String confirm) {
        Map<String, String> errors = new LinkedHashMap<>();
        AccountService.validatePassword(password, "password", errors);
        AccountService.validatePassword(confirm, "confirmPassword", errors);
        if (password != null && !password.equals(confirm)) errors.put("confirmPassword", "Mật khẩu xác nhận không khớp.");
        AccountService.rejectInvalid(errors);
        return passwordHasher.hash(password);
    }

    private static String validEmail(String email) {
        String normalized = AccountService.normalizeEmail(email);
        Map<String, String> errors = new LinkedHashMap<>();
        AccountService.validateEmail(normalized, errors);
        AccountService.rejectInvalid(errors);
        return normalized;
    }

    private static User requireUser(AccountDAO.Session session, String userId) {
        User user = session.findUser(userId, true).orElseThrow(() ->
                new AccountException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.", 401));
        AccountService.requireActive(user);
        return user;
    }

    private static boolean active(User user) {
        return user instanceof Customer customer && customer.getStatus() == CustomerStatus.ACTIVE
                || user instanceof Employee employee && employee.getStatus() == EmployeeStatus.WORKING;
    }

    private byte[] key() {
        String secret = otpSecret.get();
        if (secret == null || secret.length() < 32 || secret.length() > 512
                || secret.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new AccountException("EMAIL_NOT_CONFIGURED", "Chức năng xác thực email chưa được cấu hình.", 503);
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    private static String challengeId(String email, String purpose) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((email + "\n" + purpose).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.");
        }
    }

    private static String binding(byte[] key, User user) {
        return hmac(key, "account-binding\n" + user.getId() + "\n" + AccountService.normalizeEmail(user.getEmail())
                + "\n" + user.getPasswordHash() + "\n" + user.getPasswordVersion());
    }

    private static String codeDigest(byte[] key, String id, String salt, String code) {
        return hmac(key, "email-verification\n" + id + "\n" + salt + "\n" + code);
    }

    private static String hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HmacSHA256 is unavailable.");
        }
    }

    private static boolean constantEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static AccountException invalidCode() {
        return new AccountException("VERIFICATION_CODE_INVALID", "Mã xác thực không đúng, đã hết hạn hoặc đã sử dụng.", 400,
                Map.of("verificationCode", "Vui lòng kiểm tra mã gồm 6 chữ số hoặc yêu cầu mã mới."));
    }

    private record Delivery(String id, String email, String purpose, String code, String digest, boolean send) {
        @Override
        public String toString() { return "Delivery[verification data redacted]"; }
    }

    private record VerificationResult<T>(T value, AccountException failure) {
        static <T> VerificationResult<T> success(T value) { return new VerificationResult<>(value, null); }
        static <T> VerificationResult<T> failure(AccountException failure) { return new VerificationResult<>(null, failure); }
        T valueOrThrow() { if (failure != null) throw failure; return value; }
    }
}
