package vn.edu.crave.model.service;

import vn.edu.crave.config.DatabaseConfig;
import vn.edu.crave.model.form.LoginRequest;
import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.form.RegisterRequest;
import vn.edu.crave.model.entity.EmailVerificationChallenge;
import vn.edu.crave.model.entity.Employee;
import vn.edu.crave.model.entity.User;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.model.dao.AccountDAO;
import vn.edu.crave.util.PasswordHasher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real MySQL locking/rollback; removes only randomized accounts and challenge emails owned by this test. */
class EmailVerificationServiceIntegrationTest {
    private static final String SECRET = "integration-test-otp-pepper-minimum-32-bytes";
    private static final String PASSWORD = "OriginalPassword#123";
    private static final String NEW_PASSWORD = "UpdatedPassword#456";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static EntityManagerFactory emf;
    private final Map<String, String> accountEmails = new LinkedHashMap<>();
    private final Set<String> challengeEmails = new LinkedHashSet<>();
    private final List<Mail> mail = Collections.synchronizedList(new ArrayList<>());
    private final MutableClock clock = new MutableClock();
    private final PasswordHasher hasher = new PasswordHasher();

    @BeforeAll
    static void connect() {
        emf = DatabaseConfig.getEntityManagerFactory();
        DatabaseConfig.verifyConnection();
    }

    @AfterAll
    static void close() { DatabaseConfig.close(); }

    @AfterEach
    void removeOwnedRecords() {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            for (String email : challengeEmails) {
                em.createQuery("DELETE FROM EmailVerificationChallenge c WHERE c.email = :email")
                        .setParameter("email", email).executeUpdate();
            }
            for (var account : accountEmails.entrySet()) {
                em.createNativeQuery("DELETE FROM user_account WHERE user_id = :id AND email = :email")
                        .setParameter("id", account.getKey()).setParameter("email", account.getValue()).executeUpdate();
            }
            // Also cover a customer created by a concurrent test before an assertion interrupted ID bookkeeping.
            for (String email : challengeEmails) {
                em.createNativeQuery("DELETE FROM user_account WHERE email = :email")
                        .setParameter("email", email).executeUpdate();
            }
            em.getTransaction().commit();
        }
    }

    @Test
    void wrongCodeAttemptsPersistAcrossFreshServiceTransactionsAndFifthAttemptBlocksCorrectCode() {
        String email = newEmail();
        service().requestRegistrationCode(email);
        String correct = mail.get(0).code();
        String wrong = correct.equals("999999") ? "000000" : "999999";
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertInvalid(() -> service().register(registration(email, newPhone()), wrong));
            assertEquals(attempt, challenge(email, "REGISTER").getAttempts());
        }
        assertInvalid(() -> service().register(registration(email, newPhone()), correct));
        assertFalse(challenge(email, "REGISTER").isConsumed());
        assertEquals(5, challenge(email, "REGISTER").getAttempts());
    }

    @Test
    void registrationConflictRollsBackWithoutConsumingCodeThenValidRegistrationConsumesIt() {
        AccountService accounts = accounts();
        ProfileResponse existing = accounts.register(registration(newEmail(), newPhone()));
        accountEmails.put(existing.id(), existing.email());
        String email = newEmail();
        service().requestRegistrationCode(email);
        String code = mail.get(0).code();
        AccountException failure = assertThrows(AccountException.class,
                () -> service().register(registration(email, existing.phone()), code));
        assertEquals(409, failure.getStatusCode());
        assertFalse(challenge(email, "REGISTER").isConsumed());
        ProfileResponse registered = service().register(registration(email, newPhone()), code);
        accountEmails.put(registered.id(), email);
        assertTrue(challenge(email, "REGISTER").isConsumed());
        assertEquals(registered.id(), accounts.login(new LoginRequest(email, PASSWORD, false)).id());
        assertInvalid(() -> service().register(registration(email, newPhone()), code));
    }

    @Test
    void concurrentRegistrationsWithSameCodeCommitExactlyOneCustomer() throws Exception {
        String email = newEmail();
        service().requestRegistrationCode(email);
        String code = mail.get(0).code();
        RegisterRequest request = registration(email, newPhone());
        List<Object> outcomes = together(List.of(
                () -> attempt(() -> service().register(request, code)),
                () -> attempt(() -> service().register(request, code))));
        assertEquals(1, outcomes.stream().filter(ProfileResponse.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(AccountException.class::isInstance).count());
        ProfileResponse created = (ProfileResponse) outcomes.stream().filter(ProfileResponse.class::isInstance).findFirst().orElseThrow();
        accountEmails.put(created.id(), email);
        assertTrue(challenge(email, "REGISTER").isConsumed());
        try (EntityManager em = emf.createEntityManager()) {
            assertEquals(1L, em.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                    .setParameter("email", email).getSingleResult());
        }
    }

    @Test
    void concurrentFirstCodeRequestsSendOnceAndCommitPersistentCooldown() throws Exception {
        String email = newEmail();
        List<Object> outcomes = together(List.of(
                () -> attempt(() -> { service().requestRegistrationCode(email); return "sent"; }),
                () -> attempt(() -> { service().requestRegistrationCode(email); return "sent"; })));
        assertEquals(1, outcomes.stream().filter("sent"::equals).count());
        AccountException limited = (AccountException) outcomes.stream().filter(AccountException.class::isInstance).findFirst().orElseThrow();
        assertEquals(429, limited.getStatusCode());
        assertEquals(1, mail.size());
        assertEquals(1, challenge(email, "REGISTER").getWindowSendCount());
    }

    @Test
    void concurrentResetConsumesCodeAndChangesPasswordVersionExactlyOnce() throws Exception {
        ProfileResponse customer = provisionCustomer();
        service().requestPasswordResetCode(customer.email());
        String code = mail.get(0).code();
        List<Object> outcomes = together(List.of(
                () -> attempt(() -> { service().resetPassword(customer.email(), code, NEW_PASSWORD, NEW_PASSWORD); return "changed"; }),
                () -> attempt(() -> { service().resetPassword(customer.email(), code, NEW_PASSWORD, NEW_PASSWORD); return "changed"; })));
        assertEquals(1, outcomes.stream().filter("changed"::equals).count());
        assertEquals(1, outcomes.stream().filter(AccountException.class::isInstance).count());
        assertEquals(1, accounts().getProfile(customer.id()).credentialVersion());
        assertTrue(challenge(customer.email(), "PASSWORD_RESET").isConsumed());
        assertEquals(customer.id(), accounts().login(new LoginRequest(customer.email(), NEW_PASSWORD, false)).id());
        assertEquals(401, assertThrows(AccountException.class,
                () -> accounts().login(new LoginRequest(customer.email(), PASSWORD, false))).getStatusCode());
    }

    @Test
    void resetAndSignedInChangePersistForCustomerEmployeeAndAdminAndRejectOldBinding() {
        List<ProfileResponse> profiles = new ArrayList<>();
        profiles.add(provisionCustomer());
        profiles.add(provisionEmployee(EmployeeRole.ORDER_STAFF));
        profiles.add(provisionEmployee(EmployeeRole.ADMIN));
        for (ProfileResponse profile : profiles) {
            service().requestPasswordResetCode(profile.email());
            String resetCode = mail.get(mail.size() - 1).code();
            service().requestPasswordChangeCode(profile.id(), PASSWORD);
            String changeCode = mail.get(mail.size() - 1).code();
            service().changePassword(profile.id(), changeCode, NEW_PASSWORD, NEW_PASSWORD);
            assertEquals(1, accounts().getProfile(profile.id()).credentialVersion());
            assertInvalid(() -> service().resetPassword(profile.email(), resetCode, PASSWORD, PASSWORD));
            assertEquals(profile.id(), accounts().login(new LoginRequest(profile.email(), NEW_PASSWORD, false)).id());
            clock.advanceSeconds(60);
            service().requestPasswordResetCode(profile.email());
            String currentReset = mail.get(mail.size() - 1).code();
            service().resetPassword(profile.email(), currentReset, PASSWORD, PASSWORD);
            assertEquals(2, accounts().getProfile(profile.id()).credentialVersion());
            assertEquals(profile.id(), accounts().login(new LoginRequest(profile.email(), PASSWORD, false)).id());
        }
    }

    private EmailVerificationService service() {
        return new EmailVerificationService(new AccountDAO(emf), hasher,
                (email, code, purpose) -> mail.add(new Mail(email, code, purpose)), clock, SECRET);
    }

    private AccountService accounts() { return new AccountService(new AccountDAO(emf), hasher); }

    private ProfileResponse provisionCustomer() {
        ProfileResponse profile = accounts().register(registration(newEmail(), newPhone()));
        accountEmails.put(profile.id(), profile.email());
        return profile;
    }

    private ProfileResponse provisionEmployee(EmployeeRole role) {
        String id = "NV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String email = newEmail();
        accountEmails.put(id, email);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.persist(new Employee(id, "OTP Integration Employee", email, newPhone(), hasher.hash(PASSWORD),
                    role, null, LocalDate.of(2026, 1, 1)));
            em.getTransaction().commit();
        }
        return accounts().getProfile(id);
    }

    private EmailVerificationChallenge challenge(String email, String purpose) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("SELECT c FROM EmailVerificationChallenge c WHERE c.email = :email AND c.purpose = :purpose",
                            EmailVerificationChallenge.class).setParameter("email", email).setParameter("purpose", purpose).getSingleResult();
        }
    }

    private String newEmail() {
        String email = "otp-it-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
        challengeEmails.add(email);
        return email;
    }

    private static String newPhone() { return "08" + String.format("%09d", RANDOM.nextInt(1_000_000_000)); }
    private static RegisterRequest registration(String email, String phone) {
        return new RegisterRequest("OTP Integration Customer", email, phone, PASSWORD, PASSWORD);
    }

    private static Object attempt(Callable<Object> action) throws Exception {
        try { return action.call(); } catch (AccountException failure) { return failure; }
    }

    private static List<Object> together(List<Callable<Object>> actions) throws Exception {
        var executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) futures.add(executor.submit(() -> { start.await(); return action.call(); }));
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }
    }

    private static void assertInvalid(Runnable action) {
        assertEquals("VERIFICATION_CODE_INVALID", assertThrows(AccountException.class, action::run).getErrorCode());
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
