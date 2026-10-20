package vn.edu.crave.model.service;

import vn.edu.crave.model.form.RegisterRequest;
import vn.edu.crave.model.entity.Customer;
import vn.edu.crave.model.entity.EmailVerificationChallenge;
import vn.edu.crave.model.entity.Employee;
import vn.edu.crave.model.entity.User;
import vn.edu.crave.model.enums.CustomerStatus;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.EmployeeStatus;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.integration.VerificationEmailSender;
import vn.edu.crave.model.dao.AccountDAO;
import vn.edu.crave.util.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {
    private static final String SECRET = "test-only-otp-pepper-32-bytes-minimum";
    private static final String PASSWORD = "OriginalPassword#123";
    private static final String NEW_PASSWORD = "NewPassword#456";
    private final PasswordHasher hasher = new PasswordHasher();
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, EmailVerificationChallenge> challenges = new LinkedHashMap<>();
    private final List<Mail> mail = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private final AccountDAO.Session session = mock(AccountDAO.Session.class);
    private final AccountDAO accountDAO = mock(AccountDAO.class);
    private int commits;

    EmailVerificationServiceTest() {
        when(accountDAO.read(any())).thenAnswer(call -> ((Function<AccountDAO.Session, ?>) call.getArgument(0)).apply(session));
        when(accountDAO.write(any())).thenAnswer(call -> {
            Object result = ((Function<AccountDAO.Session, ?>) call.getArgument(0)).apply(session);
            commits++;
            return result;
        });
        when(session.findUser(anyString(), anyBoolean())).thenAnswer(call -> Optional.ofNullable(users.get(call.getArgument(0))));
        when(session.findByEmail(anyString(), anyBoolean())).thenAnswer(call -> users.values().stream()
                .filter(user -> user.getEmail().equalsIgnoreCase(call.getArgument(0))).findFirst());
        when(session.findVerificationChallenge(anyString(), anyBoolean()))
                .thenAnswer(call -> Optional.ofNullable(challenges.get(call.getArgument(0))));
        doAnswer(call -> {
            EmailVerificationChallenge challenge = call.getArgument(0);
            challenges.put(challenge.getId(), challenge);
            return null;
        }).when(session).persistVerificationChallenge(any());
        when(session.findContactConflicts(anyString(), anyString(), isNull())).thenReturn(Map.of());
        doAnswer(call -> {
            User user = call.getArgument(0);
            users.put(user.getId(), user);
            return null;
        }).when(session).persistCustomer(any());
    }

    private EmailVerificationService service() {
        return new EmailVerificationService(accountDAO, hasher,
                (email, code, purpose) -> mail.add(new Mail(email, code, purpose)), clock, SECRET);
    }

    @Test
    void pendingRegistrationHashesBeforeSendingAndSurvivesSessionSerializationWithoutPlaintextPassword() throws Exception {
        PasswordHasher registrationHasher = mock(PasswordHasher.class);
        String expectedHash = hasher.hash(PASSWORD);
        when(registrationHasher.hash(PASSWORD)).thenReturn(expectedHash);
        EmailVerificationService service = new EmailVerificationService(accountDAO, registrationHasher,
                (email, code, purpose) -> {
                    verify(registrationHasher).hash(PASSWORD);
                    assertTrue(users.isEmpty(), "Registration must wait for email verification");
                    mail.add(new Mail(email, code, purpose));
                }, clock, SECRET);

        EmailVerificationService.PendingRegistration pending = service.requestRegistrationCode(
                new RegisterRequest("  New Customer  ", "  USER@Example.COM  ", " 0991234567 ", PASSWORD, PASSWORD));
        assertEquals("New Customer", pending.getFullName());
        assertEquals("user@example.com", pending.getEmail());
        assertEquals("0991234567", pending.getPhone());
        assertEquals(clock.millis() + 600_000, pending.getExpiresAtMillis());
        assertFalse(pending.toString().contains(PASSWORD));
        assertFalse(pending.toString().contains(expectedHash));
        assertTrue(users.isEmpty());

        ByteArrayOutputStream sessionBytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(sessionBytes)) {
            output.writeObject(pending);
        }
        assertFalse(sessionBytes.toString(StandardCharsets.ISO_8859_1).contains(PASSWORD));
        EmailVerificationService.PendingRegistration restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(sessionBytes.toByteArray()))) {
            restored = (EmailVerificationService.PendingRegistration) input.readObject();
        }

        assertEquals("user@example.com", service.register(restored, mail.get(0).code()).email());
        User created = users.values().iterator().next();
        assertEquals(expectedHash, created.getPasswordHash());
        assertTrue(hasher.verify(PASSWORD, created.getPasswordHash()));
        assertTrue(challenges.values().iterator().next().isConsumed());
        assertInvalid(() -> service.register(restored, mail.get(0).code()));
        verify(registrationHasher, times(1)).hash(PASSWORD);
        verifyNoMoreInteractions(registrationHasher);
        assertEquals(1, users.size());
    }

    @Test
    void pendingRegistrationRejectsInvalidFieldsBeforeHashingOrSendingCode() {
        PasswordHasher registrationHasher = mock(PasswordHasher.class);
        EmailVerificationService service = new EmailVerificationService(accountDAO, registrationHasher,
                (email, code, purpose) -> fail("Invalid registration must not send an OTP"), clock, SECRET);
        List<RegisterRequest> invalidRequests = List.of(
                new RegisterRequest("A", "new@example.com", "0991234567", PASSWORD, PASSWORD),
                new RegisterRequest("New Customer", "invalid-email", "0991234567", PASSWORD, PASSWORD),
                new RegisterRequest("New Customer", "new@example.com", "invalid-phone", PASSWORD, PASSWORD),
                new RegisterRequest("New Customer", "new@example.com", "0991234567", "short", "short"),
                new RegisterRequest("New Customer", "new@example.com", "0991234567", PASSWORD, NEW_PASSWORD));
        for (RegisterRequest request : invalidRequests) {
            assertEquals("VALIDATION_ERROR", assertThrows(AccountException.class,
                    () -> service.requestRegistrationCode(request)).getErrorCode());
        }
        assertEquals("VALIDATION_ERROR", assertThrows(AccountException.class,
                () -> service.requestRegistrationCode((RegisterRequest) null)).getErrorCode());
        verifyNoInteractions(registrationHasher, accountDAO);
        assertTrue(challenges.isEmpty());
    }

    @Test
    void pendingRegistrationKeepsPreparedPasswordOnWrongCodeAndResendAndRefreshesExpiry() {
        PasswordHasher registrationHasher = mock(PasswordHasher.class);
        String expectedHash = hasher.hash(PASSWORD);
        when(registrationHasher.hash(PASSWORD)).thenReturn(expectedHash);
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(123456, 654321);
        EmailVerificationService service = new EmailVerificationService(accountDAO, registrationHasher,
                (email, code, purpose) -> mail.add(new Mail(email, code, purpose)), clock, () -> SECRET, random);
        EmailVerificationService.PendingRegistration pending = service.requestRegistrationCode(registration("new@example.com"));
        assertInvalid(() -> service.register(pending, "000000"));
        assertEquals(1, challenges.values().iterator().next().getAttempts());
        assertFalse(challenges.values().iterator().next().isConsumed());
        assertRateLimited(() -> service.resendRegistrationCode(pending));

        clock.advanceSeconds(60);
        EmailVerificationService.PendingRegistration refreshed = service.resendRegistrationCode(pending);
        assertNotSame(pending, refreshed);
        assertEquals(pending.getEmail(), refreshed.getEmail());
        assertEquals(pending.getExpiresAtMillis() + 60_000, refreshed.getExpiresAtMillis());
        assertEquals(2, mail.size());
        assertInvalid(() -> service.register(refreshed, mail.get(0).code()));

        clock.advanceSeconds(540);
        assertRegistrationExpired(() -> service.register(pending, mail.get(1).code()));
        service.register(refreshed, mail.get(1).code());
        assertEquals(expectedHash, users.values().iterator().next().getPasswordHash());
        verify(registrationHasher, times(1)).hash(PASSWORD);
        verifyNoMoreInteractions(registrationHasher);
    }

    @Test
    void missingOrExpiredPendingRegistrationCannotRegisterOrResend() {
        EmailVerificationService service = service();
        EmailVerificationService.PendingRegistration pending = service.requestRegistrationCode(registration("new@example.com"));
        clock.advanceSeconds(600);
        clearInvocations(accountDAO);
        assertRegistrationExpired(() -> service.register(pending, mail.get(0).code()));
        assertRegistrationExpired(() -> service.resendRegistrationCode(pending));
        assertRegistrationExpired(() -> service.register((EmailVerificationService.PendingRegistration) null, mail.get(0).code()));
        assertRegistrationExpired(() -> service.resendRegistrationCode(null));
        verifyNoInteractions(accountDAO);
        assertEquals(1, mail.size());
        assertTrue(users.isEmpty());
        assertFalse(challenges.values().iterator().next().isConsumed());
    }

    @Test
    void pendingRegistrationStillChecksContactConflictsAndPreservesCodeForRetry() {
        EmailVerificationService service = service();
        EmailVerificationService.PendingRegistration pending = service.requestRegistrationCode(registration("new@example.com"));
        when(session.findContactConflicts(pending.getEmail(), pending.getPhone(), null))
                .thenReturn(Map.of("phone", "Số điện thoại đã được sử dụng."));
        assertEquals(409, assertThrows(AccountException.class,
                () -> service.register(pending, mail.get(0).code())).getStatusCode());
        assertFalse(challenges.values().iterator().next().isConsumed());
        assertTrue(users.isEmpty());
        when(session.findContactConflicts(pending.getEmail(), pending.getPhone(), null)).thenReturn(Map.of());
        service.register(pending, mail.get(0).code());
        assertTrue(challenges.values().iterator().next().isConsumed());
        assertEquals(1, users.size());
    }

    @Test
    void registrationNormalizesEmailPreservesLeadingZerosStoresOnlySaltedHmacAndConsumesOnce() {
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(7);
        doAnswer(call -> {
            byte[] bytes = call.getArgument(0);
            java.util.Arrays.fill(bytes, (byte) 17);
            return null;
        }).when(random).nextBytes(any());
        EmailVerificationService service = new EmailVerificationService(accountDAO, hasher,
                (email, code, purpose) -> mail.add(new Mail(email, code, purpose)), clock, () -> SECRET, random);
        service.requestRegistrationCode("  USER@Example.COM  ");
        Mail sent = mail.get(0);
        assertEquals("user@example.com", sent.email());
        assertEquals("000007", sent.code());
        assertEquals("REGISTER", sent.purpose());
        EmailVerificationChallenge challenge = challenges.values().iterator().next();
        assertEquals(64, challenge.getCodeDigest().length());
        assertEquals(32, challenge.getCodeSalt().length());
        assertNotEquals(sent.code(), challenge.getCodeDigest());
        assertFalse(challenge.toString().contains(sent.code()));
        assertEquals(600_000, challenge.getExpiresAt() - challenge.getIssuedAt());
        assertEquals("user@example.com", service.register(registration("USER@example.com"), sent.code()).email());
        assertTrue(challenge.isConsumed());
        assertInvalid(() -> service.register(registration("user@example.com"), sent.code()));
        assertEquals(1, users.size());
    }

    @Test
    void wrongAttemptsCommitEvenThoughPublicMethodThrowsAndFifthLocksCorrectCode() {
        EmailVerificationService service = service();
        service.requestRegistrationCode("new@example.com");
        EmailVerificationChallenge challenge = challenges.values().iterator().next();
        String correct = mail.get(0).code();
        String wrong = correct.equals("999999") ? "000000" : "999999";
        for (int attempt = 1; attempt <= 5; attempt++) {
            int before = commits;
            assertInvalid(() -> service.register(registration("new@example.com"), wrong));
            assertEquals(before + 1, commits, "Failed verification must commit its attempt counter");
            assertEquals(attempt, challenge.getAttempts());
        }
        assertInvalid(() -> service.register(registration("new@example.com"), correct));
        assertEquals(5, challenge.getAttempts());
        assertTrue(users.isEmpty());
    }

    @Test
    void expiryAtTenMinutesAndMalformedCodeCannotRegister() {
        EmailVerificationService service = service();
        service.requestRegistrationCode("new@example.com");
        assertInvalid(() -> service.register(registration("new@example.com"), "１２３４５６"));
        assertInvalid(() -> service.register(registration("new@example.com"), null));
        clock.advanceSeconds(600);
        assertInvalid(() -> service.register(registration("new@example.com"), mail.get(0).code()));
        assertTrue(users.isEmpty());
    }

    @Test
    void resendInvalidatesOldCodeAndPersistsCooldownAndHourlyLimitAcrossServiceInstances() {
        EmailVerificationService service = service();
        service.requestRegistrationCode("new@example.com");
        String oldCode = mail.get(0).code();
        String oldSalt = challenges.values().iterator().next().getCodeSalt();
        assertRateLimited(() -> service().requestRegistrationCode("new@example.com"));
        for (int sent = 2; sent <= 5; sent++) {
            clock.advanceSeconds(60);
            service().requestRegistrationCode("new@example.com");
        }
        assertEquals(5, mail.size());
        assertNotEquals(oldSalt, challenges.values().iterator().next().getCodeSalt());
        clock.advanceSeconds(60);
        assertRateLimited(() -> service().requestRegistrationCode("new@example.com"));
        String latestCode = mail.get(mail.size() - 1).code();
        if (!latestCode.equals(oldCode)) assertInvalid(() -> service.register(registration("new@example.com"), oldCode));
        clock.advanceSeconds(3600);
        service().requestRegistrationCode("new@example.com");
        assertEquals(6, mail.size());
    }

    @Test
    void hourlyLimitUsesRollingHistoryAcrossTheFirstSendWindowBoundary() {
        EmailVerificationService service = service();
        service.requestRegistrationCode("new@example.com");
        clock.advanceSeconds(3300);
        for (int sent = 2; sent <= 5; sent++) {
            service.requestRegistrationCode("new@example.com");
            clock.advanceSeconds(60);
        }
        // The original send has expired from the rolling hour, but the last four have not.
        clock.advanceSeconds(60);
        service.requestRegistrationCode("new@example.com");
        clock.advanceSeconds(60);
        assertRateLimited(() -> service.requestRegistrationCode("new@example.com"));
        assertEquals(6, mail.size());
    }

    @Test
    void unknownInactiveAndActiveResetRequestsHaveIdenticalRateLimitsButOnlyActiveReceivesMail() {
        Customer active = customer("KH1", "active@example.com");
        Customer inactive = customer("KH2", "inactive@example.com");
        inactive.setStatus(CustomerStatus.LOCKED);
        users.put(active.getId(), active);
        users.put(inactive.getId(), inactive);
        EmailVerificationService service = service();
        for (String email : List.of(active.getEmail(), inactive.getEmail(), "unknown@example.com")) {
            service.requestPasswordResetCode(email);
            assertRateLimited(() -> service.requestPasswordResetCode(email));
        }
        assertEquals(1, mail.size());
        assertEquals(active.getEmail(), mail.get(0).email());
        assertEquals(3, challenges.size());
        assertInvalid(() -> service.resetPassword("unknown@example.com", "000000", NEW_PASSWORD, NEW_PASSWORD));
        assertInvalid(() -> service.resetPassword(inactive.getEmail(), "000000", NEW_PASSWORD, NEW_PASSWORD));
    }

    @Test
    void resetWorksForCustomersEmployeesAndAdminsAndIncrementsCredentialVersion() {
        for (User user : List.of(customer("KH1", "customer@example.com"),
                employee("NV1", "staff@example.com", EmployeeRole.ORDER_STAFF),
                employee("NV2", "admin@example.com", EmployeeRole.ADMIN))) {
            users.put(user.getId(), user);
            EmailVerificationService service = service();
            service.requestPasswordResetCode(user.getEmail());
            String code = mail.get(mail.size() - 1).code();
            service.resetPassword(user.getEmail(), code, NEW_PASSWORD, NEW_PASSWORD);
            assertTrue(hasher.verify(NEW_PASSWORD, user.getPasswordHash()));
            assertFalse(hasher.verify(PASSWORD, user.getPasswordHash()));
            assertEquals(1, user.getPasswordVersion());
            assertInvalid(() -> service.resetPassword(user.getEmail(), code, PASSWORD, PASSWORD));
        }
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndTargetsActualUserEmailWithoutCrossPurposeReuse() {
        Customer owner = customer("KH1", "owner@example.com");
        Customer other = customer("KH2", "other@example.com");
        users.put(owner.getId(), owner);
        users.put(other.getId(), other);
        EmailVerificationService service = service();
        AccountException failure = assertThrows(AccountException.class,
                () -> service.requestPasswordChangeCode(owner.getId(), "WrongPassword123"));
        assertEquals(400, failure.getStatusCode());
        assertEquals("INVALID_CURRENT_PASSWORD", failure.getErrorCode());
        assertTrue(mail.isEmpty());
        service.requestPasswordChangeCode(owner.getId(), PASSWORD);
        Mail sent = mail.get(0);
        assertEquals(owner.getEmail(), sent.email());
        assertEquals("PASSWORD_CHANGE", sent.purpose());
        assertInvalid(() -> service.resetPassword(owner.getEmail(), sent.code(), NEW_PASSWORD, NEW_PASSWORD));
        assertInvalid(() -> service.changePassword(other.getId(), sent.code(), NEW_PASSWORD, NEW_PASSWORD));
        service.changePassword(owner.getId(), sent.code(), NEW_PASSWORD, NEW_PASSWORD);
        assertEquals(1, owner.getPasswordVersion());
        assertEquals(0, other.getPasswordVersion());
        assertTrue(hasher.verify(NEW_PASSWORD, owner.getPasswordHash()));
    }

    @Test
    void changingPasswordOrEmailAfterIssuanceRejectsStaleChallenge() {
        Customer owner = customer("KH1", "owner@example.com");
        users.put(owner.getId(), owner);
        EmailVerificationService service = service();
        service.requestPasswordResetCode(owner.getEmail());
        String resetCode = mail.get(0).code();
        owner.setPasswordHash(hasher.hash("OtherNewPassword123"));
        assertInvalid(() -> service.resetPassword(owner.getEmail(), resetCode, NEW_PASSWORD, NEW_PASSWORD));
        assertEquals(0, owner.getPasswordVersion());
        service.requestPasswordChangeCode(owner.getId(), "OtherNewPassword123");
        String changeCode = mail.get(1).code();
        owner.setEmail("updated@example.com");
        assertInvalid(() -> service.changePassword(owner.getId(), changeCode, NEW_PASSWORD, NEW_PASSWORD));
        assertTrue(hasher.verify("OtherNewPassword123", owner.getPasswordHash()));
    }

    @Test
    void inactiveAccountsCannotUsePreviouslyIssuedCodesAndNewPasswordValidationPreservesChallenge() {
        Employee employee = employee("NV1", "employee@example.com", EmployeeRole.ADMIN);
        users.put(employee.getId(), employee);
        EmailVerificationService service = service();
        service.requestPasswordResetCode(employee.getEmail());
        String code = mail.get(0).code();
        assertEquals(400, assertThrows(AccountException.class,
                () -> service.resetPassword(employee.getEmail(), code, "short", "other")).getStatusCode());
        assertFalse(challenges.values().iterator().next().isConsumed());
        employee.setStatus(EmployeeStatus.ON_LEAVE);
        assertInvalid(() -> service.resetPassword(employee.getEmail(), code, NEW_PASSWORD, NEW_PASSWORD));
        assertEquals(401, assertThrows(AccountException.class,
                () -> service.requestPasswordChangeCode(employee.getId(), PASSWORD)).getStatusCode());
    }

    @Test
    void mailFailureInvalidatesChallengeKeepsCountersAndResetAcknowledgementStaysGeneric() {
        VerificationEmailSender brokenSender = (email, code, purpose) -> { throw new RuntimeException("private SMTP detail"); };
        EmailVerificationService service = new EmailVerificationService(accountDAO, hasher, brokenSender, clock, SECRET);
        AccountException failure = assertThrows(AccountException.class, () -> service.requestRegistrationCode("new@example.com"));
        assertEquals(503, failure.getStatusCode());
        assertFalse(failure.getMessage().contains("private"));
        assertTrue(challenges.values().iterator().next().isConsumed());
        assertRateLimited(() -> service.requestRegistrationCode("new@example.com"));
        Customer owner = customer("KH1", "owner@example.com");
        users.put(owner.getId(), owner);
        assertDoesNotThrow(() -> service.requestPasswordResetCode(owner.getEmail()));
        assertDoesNotThrow(() -> service.requestPasswordResetCode("unknown@example.com"));
        assertRateLimited(() -> service.requestPasswordResetCode(owner.getEmail()));
        assertRateLimited(() -> service.requestPasswordResetCode("unknown@example.com"));
    }

    @Test
    void missingSecretIsLazyAndCodeCannotBeVerifiedWithDifferentPepper() {
        assertDoesNotThrow(() -> new EmailVerificationService());
        EmailVerificationService unavailable = new EmailVerificationService(accountDAO, hasher,
                (email, code, purpose) -> fail("Must not send without secret"), clock, "");
        assertEquals(503, assertThrows(AccountException.class,
                () -> unavailable.requestRegistrationCode("new@example.com")).getStatusCode());
        EmailVerificationService service = service();
        service.requestRegistrationCode("new@example.com");
        EmailVerificationService rotated = new EmailVerificationService(accountDAO, hasher,
                (email, code, purpose) -> {}, clock, SECRET + "rotated");
        assertInvalid(() -> rotated.register(registration("new@example.com"), mail.get(0).code()));
        assertTrue(users.isEmpty());
    }

    private Customer customer(String id, String email) {
        return new Customer(id, "Test Customer", email, "0901234567", hasher.hash(PASSWORD));
    }

    private Employee employee(String id, String email, EmployeeRole role) {
        return new Employee(id, "Test Employee", email, "0911234567", hasher.hash(PASSWORD), role,
                null, LocalDate.of(2026, 1, 1));
    }

    private static RegisterRequest registration(String email) {
        return new RegisterRequest("New Customer", email, "0991234567", PASSWORD, PASSWORD);
    }

    private static void assertInvalid(Runnable operation) {
        AccountException failure = assertThrows(AccountException.class, operation::run);
        assertEquals(400, failure.getStatusCode());
        assertEquals("VERIFICATION_CODE_INVALID", failure.getErrorCode());
    }

    private static void assertRateLimited(Runnable operation) {
        assertEquals(429, assertThrows(AccountException.class, operation::run).getStatusCode());
    }

    private static void assertRegistrationExpired(Runnable operation) {
        AccountException failure = assertThrows(AccountException.class, operation::run);
        assertEquals(400, failure.getStatusCode());
        assertEquals("REGISTRATION_EXPIRED", failure.getErrorCode());
    }

    private record Mail(String email, String code, String purpose) { }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-13T08:00:00Z");
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
