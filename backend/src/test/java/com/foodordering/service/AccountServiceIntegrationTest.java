package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.dto.AddressResponse;
import com.foodordering.dto.AddressWriteRequest;
import com.foodordering.dto.LoginRequest;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.ProfileUpdateRequest;
import com.foodordering.dto.RegisterRequest;
import com.foodordering.entity.Customer;
import com.foodordering.entity.CustomerOrder;
import com.foodordering.entity.Address;
import com.foodordering.entity.Employee;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.enums.FulfillmentType;
import com.foodordering.exception.AccountException;
import com.foodordering.repository.JpaAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses the existing MySQL schema; creates and removes only records owned by this test. */
class AccountServiceIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static EntityManagerFactory emf;
    private final Map<String, String> accountEmails = new LinkedHashMap<>();
    private final Map<String, String> orderOwners = new LinkedHashMap<>();

    @BeforeAll
    static void connect() {
        emf = DatabaseConfig.getEntityManagerFactory();
        DatabaseConfig.verifyConnection();
    }

    @AfterAll
    static void close() {
        DatabaseConfig.close();
    }

    @AfterEach
    void removeTestRecords() {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                for (Map.Entry<String, String> order : orderOwners.entrySet()) {
                    em.createNativeQuery("""
                                    DELETE FROM customer_order WHERE order_id = :id AND customer_id = :customerId
                                    AND EXISTS (SELECT 1 FROM user_account WHERE user_id = :customerId AND email = :email)
                                    """)
                            .setParameter("id", order.getKey()).setParameter("customerId", order.getValue())
                            .setParameter("email", accountEmails.get(order.getValue())).executeUpdate();
                }
                for (Map.Entry<String, String> account : accountEmails.entrySet()) {
                    em.createNativeQuery("DELETE FROM user_account WHERE user_id = :id AND email = :email")
                            .setParameter("id", account.getKey()).setParameter("email", account.getValue())
                            .executeUpdate();
                }
                em.getTransaction().commit();
            } catch (RuntimeException e) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }
    }

    @Test
    void registrationLoginProfileAndAddressCrudPersistWithOwnershipAndPreserveOrderSnapshots() {
        AccountService service = new AccountService(new JpaAccountRepository(emf));
        ProfileResponse owner = register(service);
        ProfileResponse other = register(service);
        assertEquals(owner, service.login(new LoginRequest(owner.email().toUpperCase(), "Integration#123", false)));
        assertEquals(owner.id(), service.getProfile(owner.id()).id());
        ProfileResponse profile = service.updateProfile(owner.id(),
                new ProfileUpdateRequest("Integration Updated", owner.email(), owner.phone()));
        assertEquals("Integration Updated", service.getProfile(owner.id()).fullName());
        assertEquals(owner.email(), profile.email());
        AccountException duplicate = assertThrows(AccountException.class, () -> service.updateProfile(owner.id(),
                new ProfileUpdateRequest("Attempted duplicate", other.email(), owner.phone())));
        assertEquals(409, duplicate.getStatusCode());
        assertTrue(duplicate.getDetails().containsKey("email"));
        assertEquals("Integration Updated", service.getProfile(owner.id()).fullName());

        AddressResponse first = service.createAddress(owner.id(), write("Original integration address", false));
        AddressResponse second = service.createAddress(owner.id(), write("Second integration address", false));
        assertTrue(first.isDefault());
        assertFalse(second.isDefault());
        assertNotNull(first.createdAt());
        assertNotNull(first.updatedAt());
        assertEquals(2, service.listAddresses(owner.id()).size());
        assertTrue(service.listAddresses(other.id()).isEmpty());
        assertEquals(404, assertThrows(AccountException.class,
                () -> service.getAddress(other.id(), first.id())).getStatusCode());
        assertEquals(404, assertThrows(AccountException.class,
                () -> service.updateAddress(other.id(), first.id(), write("Hijacked", true))).getStatusCode());
        assertEquals(404, assertThrows(AccountException.class,
                () -> service.deleteAddress(other.id(), first.id())).getStatusCode());

        String orderId = createOrder(owner.id(), first.addressLine());
        AddressResponse changed = service.updateAddress(owner.id(), first.id(), write("Updated integration address", false));
        assertFalse(changed.isDefault());
        assertEquals("Updated integration address", service.getAddress(owner.id(), first.id()).addressLine());
        assertTrue(service.getAddress(owner.id(), second.id()).isDefault());
        service.updateAddress(owner.id(), first.id(), write("Updated integration address", true));
        assertTrue(service.getAddress(owner.id(), first.id()).isDefault());
        service.deleteAddress(owner.id(), first.id());
        assertTrue(service.getAddress(owner.id(), second.id()).isDefault());
        try (EntityManager em = emf.createEntityManager()) {
            assertEquals("Original integration address", em.find(CustomerOrder.class, orderId).getDeliveryAddress());
        }
        service.deleteAddress(owner.id(), second.id());
        assertTrue(service.listAddresses(owner.id()).isEmpty());
    }

    @Test
    void concurrentAddressCreationSerializesDefaultChangesAndKeepsExactlyOneDefault() throws Exception {
        AccountService service = new AccountService(new JpaAccountRepository(emf));
        ProfileResponse owner = register(service);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<AddressResponse> first = executor.submit(() -> {
                start.await();
                return service.createAddress(owner.id(), write("Concurrent first address", true));
            });
            Future<AddressResponse> second = executor.submit(() -> {
                start.await();
                return service.createAddress(owner.id(), write("Concurrent second address", true));
            });
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            List<AddressResponse> addresses = service.listAddresses(owner.id());
            assertEquals(2, addresses.size());
            assertEquals(1, addresses.stream().filter(AddressResponse::isDefault).count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void databaseUniquenessViolationReturnsFieldConflictAndRollsBackInsert() {
        AccountService service = new AccountService(new JpaAccountRepository(emf));
        ProfileResponse owner = register(service);
        String conflictingId = "KH" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        accountEmails.put(conflictingId, owner.email());
        JpaAccountRepository repository = new JpaAccountRepository(emf);
        AccountException exception = assertThrows(AccountException.class, () -> repository.write(session -> {
            session.persistCustomer(new Customer(conflictingId, "Conflicting User", owner.email(), newPhone(),
                    "not-a-login-hash"));
            session.flush();
            return null;
        }));

        assertEquals(409, exception.getStatusCode());
        assertTrue(exception.getDetails().containsKey("email"));
        try (EntityManager em = emf.createEntityManager()) {
            assertEquals(0L, em.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class)
                    .setParameter("id", conflictingId).getSingleResult());
        }
        assertEquals(owner.id(), service.getProfile(owner.id()).id());
    }

    @Test
    void mixedConcurrentDefaultMutationsAndDeletingLastAddressSerializeWithoutLosingDefaults() throws Exception {
        AccountService service = new AccountService(new JpaAccountRepository(emf));
        ProfileResponse owner = register(service);
        AddressResponse first = service.createAddress(owner.id(), write("First address", true));
        AddressResponse second = service.createAddress(owner.id(), write("Second address", false));
        service.createAddress(owner.id(), write("Third address", false));

        runTogether(List.of(
                () -> {
                    service.deleteAddress(owner.id(), first.id());
                    return null;
                },
                () -> service.updateAddress(owner.id(), second.id(), write("Second revised", true)),
                () -> service.createAddress(owner.id(), write("Concurrent new default", true))));
        List<AddressResponse> remaining = service.listAddresses(owner.id());
        assertEquals(3, remaining.size());
        assertEquals(1, remaining.stream().filter(AddressResponse::isDefault).count());
        assertTrue(remaining.stream().noneMatch(address -> address.id().equals(first.id())));
        assertEquals("Second revised", service.getAddress(owner.id(), second.id()).addressLine());

        for (AddressResponse address : remaining) service.deleteAddress(owner.id(), address.id());
        AddressResponse last = service.createAddress(owner.id(), write("Last old address", false));
        runTogether(List.of(
                () -> {
                    service.deleteAddress(owner.id(), last.id());
                    return null;
                },
                () -> service.createAddress(owner.id(), write("Replacement address", false))));
        List<AddressResponse> replacement = service.listAddresses(owner.id());
        assertEquals(1, replacement.size());
        assertTrue(replacement.get(0).isDefault());
        assertEquals("Replacement address", replacement.get(0).addressLine());
    }

    @Test
    void constraintFailureAfterFlushingDefaultClearRollsBackAllAddressChanges() {
        JpaAccountRepository repository = new JpaAccountRepository(emf);
        AccountService service = new AccountService(repository);
        ProfileResponse owner = register(service);
        ProfileResponse other = register(service);
        AddressResponse original = service.createAddress(owner.id(), write("Original default", true));
        AddressResponse foreign = service.createAddress(other.id(), write("Other customer's address", true));

        AccountException failure = assertThrows(AccountException.class, () -> repository.write(session -> {
            Customer locked = session.findCustomer(owner.id(), true).orElseThrow();
            Address current = session.findAddress(owner.id(), original.id()).orElseThrow();
            current.setDefaultAddress(false);
            session.flush();
            Address duplicate = new Address(foreign.id(), "Must roll back", null, true);
            duplicate.setCustomer(locked);
            session.persistAddress(duplicate);
            session.flush();
            return null;
        }));

        assertEquals(409, failure.getStatusCode());
        assertEquals(1, service.listAddresses(owner.id()).size());
        assertTrue(service.getAddress(owner.id(), original.id()).isDefault());
        assertEquals("Original default", service.getAddress(owner.id(), original.id()).addressLine());
        assertEquals("Other customer's address", service.getAddress(other.id(), foreign.id()).addressLine());
        assertTrue(service.getAddress(other.id(), foreign.id()).isDefault());
    }

    @Test
    void concurrentProfileContactClaimsCommitExactlyOneAndLeaveLoserUnchanged() throws Exception {
        AccountService service = new AccountService(new JpaAccountRepository(emf));
        ProfileResponse first = register(service);
        ProfileResponse second = register(service);
        String claimedPhone = newPhone();
        List<Object> outcomes = runTogether(List.of(
                () -> claimContacts(service, first.id(), first.email(), claimedPhone),
                () -> claimContacts(service, second.id(), second.email(), claimedPhone)));

        assertEquals(1, outcomes.stream().filter(ProfileResponse.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(AccountException.class::isInstance).count());
        for (int index = 0; index < outcomes.size(); index++) {
            ProfileResponse original = index == 0 ? first : second;
            ProfileResponse stored = service.getProfile(original.id());
            if (outcomes.get(index) instanceof AccountException failure) {
                assertEquals(409, failure.getStatusCode());
                assertFalse(failure.getDetails().isEmpty());
                assertEquals(original, stored);
            } else {
                assertEquals(original.email(), stored.email());
                assertEquals(claimedPhone, stored.phone());
            }
        }
    }

    @Test
    void employeeAuthorizationAndInactiveAccountsAreEnforcedAgainstRealDatabaseRecords() {
        AccountService service = new AccountService(new JpaAccountRepository(emf));
        ProfileResponse customer = register(service);
        String employeeId = "NV" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String employeeEmail = "it-employee-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
        accountEmails.put(employeeId, employeeEmail);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            String hash = em.find(Customer.class, customer.id()).getPasswordHash();
            em.persist(new Employee(employeeId, "Integration Employee", employeeEmail, newPhone(), hash,
                    EmployeeRole.ORDER_STAFF, null, LocalDate.of(2026, 1, 1)));
            em.getTransaction().commit();
        }
        ProfileResponse employee = service.login(new LoginRequest(employeeEmail, "Integration#123", false));
        assertEquals(AccountType.EMPLOYEE, employee.accountType());
        assertEquals(EmployeeRole.ORDER_STAFF, employee.role());
        assertEquals(403, assertThrows(AccountException.class, () -> service.updateProfile(employeeId,
                new ProfileUpdateRequest(employee.fullName(), employee.email(), employee.phone()))).getStatusCode());
        assertEquals(403, assertThrows(AccountException.class, () -> service.listAddresses(employeeId)).getStatusCode());

        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            em.find(Employee.class, employeeId).setStatus(EmployeeStatus.ON_LEAVE);
            em.find(Customer.class, customer.id()).setStatus(CustomerStatus.LOCKED);
            em.getTransaction().commit();
        }
        assertEquals(401, assertThrows(AccountException.class, () -> service.getProfile(employeeId)).getStatusCode());
        assertEquals(401, assertThrows(AccountException.class,
                () -> service.login(new LoginRequest(employeeEmail, "Integration#123", false))).getStatusCode());
        assertEquals(401, assertThrows(AccountException.class, () -> service.getProfile(customer.id())).getStatusCode());
        assertEquals(401, assertThrows(AccountException.class,
                () -> service.login(new LoginRequest(customer.email(), "Integration#123", false))).getStatusCode());
        assertEquals(401, assertThrows(AccountException.class, () -> service.listAddresses(customer.id())).getStatusCode());
        assertEquals(401, assertThrows(AccountException.class,
                () -> service.createAddress(customer.id(), write("Unauthorized new address", true))).getStatusCode());
    }

    @Test
    void databasePhoneUniquenessReturnsFieldConflictAndDoesNotLeavePartialAccount() {
        AccountService service = new AccountService(new JpaAccountRepository(emf));
        ProfileResponse owner = register(service);
        String id = "KH" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String email = "it-phone-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
        accountEmails.put(id, email);
        AccountException failure = assertThrows(AccountException.class,
                () -> new JpaAccountRepository(emf).write(session -> {
                    session.persistCustomer(new Customer(id, "Phone conflict", email, owner.phone(), "unsupported"));
                    session.flush();
                    return null;
                }));

        assertEquals(409, failure.getStatusCode());
        assertTrue(failure.getDetails().containsKey("phone"));
        try (EntityManager em = emf.createEntityManager()) {
            assertEquals(0L, em.createQuery("SELECT COUNT(u) FROM User u WHERE u.id = :id", Long.class)
                    .setParameter("id", id).getSingleResult());
        }
    }

    private static Object claimContacts(AccountService service, String id, String email, String phone) {
        try {
            return service.updateProfile(id, new ProfileUpdateRequest("Contact Winner", email, phone));
        } catch (AccountException failure) {
            return failure;
        }
    }

    private static <T> List<T> runTogether(List<Callable<T>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> action : actions) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private ProfileResponse register(AccountService service) {
        String email = "it-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
        ProfileResponse customer = service.register(new RegisterRequest("Integration Customer", email, newPhone(),
                "Integration#123", "Integration#123"));
        accountEmails.put(customer.id(), customer.email());
        return customer;
    }

    private String createOrder(String customerId, String addressLine) {
        String id = "OT" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        orderOwners.put(id, customerId);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                CustomerOrder order = new CustomerOrder();
                order.setId(id);
                order.setCustomer(em.getReference(Customer.class, customerId));
                order.setFulfillmentType(FulfillmentType.DELIVERY);
                order.setReceiverName("Integration Receiver");
                order.setReceiverPhone(newPhone());
                order.setDeliveryAddress(addressLine);
                em.persist(order);
                em.getTransaction().commit();
            } catch (RuntimeException e) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw e;
            }
        }
        return id;
    }

    private static String newPhone() {
        return "09" + String.format("%09d", RANDOM.nextInt(1_000_000_000));
    }

    private static AddressWriteRequest write(String line, boolean isDefault) {
        return new AddressWriteRequest(line, null, isDefault);
    }
}
