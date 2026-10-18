package vn.edu.crave.model.service;

import vn.edu.crave.config.DatabaseConfig;
import vn.edu.crave.model.viewmodel.AdminEmployeeResponse;
import vn.edu.crave.model.form.AdminPromotionRequest;
import vn.edu.crave.model.form.EmployeeCreateRequest;
import vn.edu.crave.model.viewmodel.PromotionDto;
import vn.edu.crave.model.entity.Customer;
import vn.edu.crave.model.entity.Employee;
import vn.edu.crave.model.entity.User;
import vn.edu.crave.model.enums.CustomerStatus;
import vn.edu.crave.model.enums.DiscountType;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.EmployeeStatus;
import vn.edu.crave.model.enums.PromotionStatus;
import vn.edu.crave.exception.ConflictException;
import vn.edu.crave.exception.ForbiddenException;
import vn.edu.crave.util.PasswordHasher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Uses real MySQL and removes only the unique accounts/promotions created by this test. */
class AdminManagementIntegrationTest {
    private static EntityManagerFactory emf;
    private static String passwordHash;
    private final String token = UUID.randomUUID().toString().replace("-", "");
    private final String adminId = "ZA" + token.substring(0, 8);
    private final String hrId = "ZH" + token.substring(0, 8);
    private final String staffId = "ZS" + token.substring(0, 8);
    private final String customerId = "ZC" + token.substring(0, 8);
    private final String fixtureName = "Admin integration " + token;
    private final List<String> ownedUserIds = new ArrayList<>();
    private final List<String> ownedPromotionIds = new ArrayList<>();
    private AdminAccountService accounts;
    private AdminPromotionService promotions;

    @BeforeAll
    static void connect() {
        emf = DatabaseConfig.getEntityManagerFactory();
        DatabaseConfig.verifyConnection();
        passwordHash = new PasswordHasher().hash("Integration#123");
    }

    @AfterAll
    static void disconnect() {
        DatabaseConfig.close();
    }

    @BeforeEach
    void fixtures() {
        accounts = new AdminAccountService(emf);
        promotions = new AdminPromotionService(emf);
        ownedUserIds.addAll(List.of(adminId, hrId, staffId, customerId));
        transaction(em -> {
            em.persist(employee(adminId, EmployeeRole.ADMIN));
            em.persist(employee(hrId, EmployeeRole.HR_MANAGER));
            em.persist(employee(staffId, EmployeeRole.MENU_MANAGER));
            em.persist(new Customer(customerId, fixtureName, email(customerId), phone(), passwordHash));
        });
    }

    @AfterEach
    void cleanOnlyOwnedFixtures() {
        transaction(em -> {
            for (String id : ownedUserIds) {
                em.createNativeQuery("DELETE FROM user_account WHERE user_id = :id AND full_name = :name")
                        .setParameter("id", id).setParameter("name", fixtureName).executeUpdate();
            }
            for (String id : ownedPromotionIds) {
                em.createNativeQuery("DELETE FROM promotion WHERE promotion_id = :id AND name = :name")
                        .setParameter("id", id).setParameter("name", fixtureName).executeUpdate();
            }
        });
    }

    @Test
    void listsExposeSafeDtosAndCustomerStatusPersists() throws Exception {
        var customer = accounts.listCustomers().stream().filter(c -> c.id().equals(customerId)).findFirst().orElseThrow();
        assertEquals(CustomerStatus.ACTIVE, customer.status());
        assertEquals(0, customer.orderCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(customer.totalSpent()));
        var employees = accounts.listEmployees();
        assertTrue(employees.stream().anyMatch(employee -> employee.id().equals(staffId)));
        for (var component : AdminEmployeeResponse.class.getRecordComponents()) {
            assertFalse(component.getName().toLowerCase().contains("password"));
            assertFalse(component.getName().toLowerCase().contains("hash"));
        }
        assertFalse(employees.toString().contains(passwordHash));
        assertFalse(employees.toString().contains("pbkdf2_sha256"));

        assertEquals(CustomerStatus.LOCKED, accounts.updateCustomerStatus(hrId, customerId, CustomerStatus.LOCKED).status());
        try (EntityManager em = emf.createEntityManager()) {
            assertEquals(CustomerStatus.LOCKED, em.find(Customer.class, customerId).getStatus());
        }
        assertEquals(CustomerStatus.ACTIVE, accounts.updateCustomerStatus(adminId, customerId, CustomerStatus.ACTIVE).status());
    }

    @Test
    void employeeCreationHashesPasswordAndReportsContactConflicts() {
        EmployeeCreateRequest request = new EmployeeCreateRequest(fixtureName, email("created"), phone(),
                "Created#123", EmployeeRole.ORDER_STAFF, "123 Nguyễn Huệ", LocalDate.of(2026, 10, 1));
        AdminEmployeeResponse created = accounts.createEmployee(hrId, request);
        ownedUserIds.add(created.id());

        assertEquals(EmployeeRole.ORDER_STAFF, created.role());
        assertEquals(EmployeeStatus.WORKING, created.status());
        try (EntityManager em = emf.createEntityManager()) {
            User stored = em.find(User.class, created.id());
            assertNotEquals(request.password(), stored.getPasswordHash());
            assertTrue(new PasswordHasher().verify(request.password(), stored.getPasswordHash()));
        }
        assertThrows(ConflictException.class, () -> accounts.createEmployee(adminId, request));
    }

    @Test
    void hrCanManageStaffButCannotCreateOrModifyAdmins() {
        assertEquals(EmployeeRole.ORDER_STAFF, accounts.updateEmployeeRole(hrId, staffId, EmployeeRole.ORDER_STAFF).role());
        assertEquals(EmployeeStatus.ON_LEAVE, accounts.updateEmployeeStatus(hrId, staffId, EmployeeStatus.ON_LEAVE).status());
        assertThrows(ForbiddenException.class, () -> accounts.updateEmployeeRole(hrId, staffId, EmployeeRole.ADMIN));
        assertThrows(ForbiddenException.class, () -> accounts.updateEmployeeStatus(hrId, adminId, EmployeeStatus.ON_LEAVE));
        assertThrows(ForbiddenException.class, () -> accounts.createEmployee(hrId,
                new EmployeeCreateRequest(fixtureName, email("forbidden"), phone(), "Created#123",
                        EmployeeRole.ADMIN, null, LocalDate.now())));
    }

    @Test
    void administratorCannotDeactivateOrDemoteOwnSessionAccount() {
        assertThrows(ConflictException.class, () -> accounts.updateEmployeeStatus(adminId, adminId, EmployeeStatus.ON_LEAVE));
        assertThrows(ConflictException.class, () -> accounts.updateEmployeeRole(adminId, adminId, EmployeeRole.MENU_MANAGER));
        try (EntityManager em = emf.createEntityManager()) {
            Employee stored = em.find(Employee.class, adminId);
            assertEquals(EmployeeRole.ADMIN, stored.getRole());
            assertEquals(EmployeeStatus.WORKING, stored.getStatus());
        }
    }

    @Test
    void simultaneousCrossDemotionLeavesAnAuthorizedAdministrator() throws Exception {
        accounts.updateEmployeeRole(adminId, staffId, EmployeeRole.ADMIN);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = workers.submit(() -> demoteAfterBarrier(adminId, staffId, ready, start));
            Future<Object> second = workers.submit(() -> demoteAfterBarrier(staffId, adminId, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Object> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(AdminEmployeeResponse.class::isInstance).count());
            assertEquals(1, outcomes.stream().filter(ForbiddenException.class::isInstance).count());
            try (EntityManager em = emf.createEntityManager()) {
                long remaining = em.createQuery("SELECT COUNT(e) FROM Employee e WHERE e.id IN :ids AND e.role = :role AND e.status = :status", Long.class)
                        .setParameter("ids", List.of(adminId, staffId)).setParameter("role", EmployeeRole.ADMIN)
                        .setParameter("status", EmployeeStatus.WORKING).getSingleResult();
                assertEquals(1, remaining);
            }
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void promotionCreateUpdateAndStatusPersistWithoutDeletingSnapshots() {
        PromotionDto created = promotions.createPromotion(promotionRequest("IP" + token.substring(0, 20), DiscountType.PERCENT, "10"));
        ownedPromotionIds.add(created.getPromotionId());
        assertTrue(promotions.listPromotions().stream().anyMatch(p -> p.getPromotionId().equals(created.getPromotionId())));
        assertThrows(ConflictException.class, () -> promotions.createPromotion(promotionRequest(created.getCode(), DiscountType.PERCENT, "20")));

        PromotionDto updated = promotions.updatePromotion(created.getPromotionId(), promotionRequest(created.getCode(), DiscountType.FIXED_AMOUNT, "20000"));
        assertEquals(DiscountType.FIXED_AMOUNT, updated.getDiscountType());
        assertEquals(0, new BigDecimal("20000").compareTo(updated.getDiscountValue()));
        assertEquals(PromotionStatus.INACTIVE, promotions.updatePromotionStatus(created.getPromotionId(), PromotionStatus.INACTIVE).getStatus());
        PromotionDto listed = promotions.listPromotions().stream().filter(p -> p.getPromotionId().equals(created.getPromotionId())).findFirst().orElseThrow();
        assertEquals(PromotionStatus.INACTIVE, listed.getStatus());
    }

    private Object demoteAfterBarrier(String actor, String target, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
        try {
            return accounts.updateEmployeeRole(actor, target, EmployeeRole.MENU_MANAGER);
        } catch (ForbiddenException exception) {
            return exception;
        }
    }

    private AdminPromotionRequest promotionRequest(String code, DiscountType type, String value) {
        return new AdminPromotionRequest(code, fixtureName, type, new BigDecimal(value), new BigDecimal("50000"),
                new BigDecimal("30000"), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), PromotionStatus.ACTIVE);
    }

    private Employee employee(String id, EmployeeRole role) {
        return new Employee(id, fixtureName, email(id), phone(), passwordHash, role, null, LocalDate.of(2026, 10, 1));
    }

    private String email(String name) {
        return "admin-it-" + name + "-" + token + "@example.com";
    }

    private String phone() {
        return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private void transaction(Consumer<EntityManager> work) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                work.accept(em);
                em.getTransaction().commit();
            } catch (RuntimeException | Error exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        }
    }
}
