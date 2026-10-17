package vn.edu.crave.model.entity;

import vn.edu.crave.config.DatabaseConfig;
import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.EmployeeStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountPersistenceIntegrationTest {

    private static EntityManagerFactory emf;

    @BeforeAll
    static void setUp() {
        // Triggers persistence unit creation with hibernate.hbm2ddl.auto=validate
        emf = DatabaseConfig.getEntityManagerFactory();
        DatabaseConfig.verifyConnection();
    }

    @AfterAll
    static void tearDown() {
        DatabaseConfig.close();
    }

    @Test
    @DisplayName("EntityManagerFactory initializes successfully with schema validation enabled")
    void entityManagerFactoryIsOpenAndValid() {
        assertNotNull(emf);
        assertTrue(emf.isOpen());
    }

    @Test
    @DisplayName("Customer entity can be loaded via User hierarchy and has addresses")
    void canLoadCustomerAccount() {
        try (EntityManager em = emf.createEntityManager()) {
            User user = em.find(User.class, "KH01");
            assertNotNull(user, "User KH01 should exist in seeded database");
            assertInstanceOf(Customer.class, user);

            Customer customer = (Customer) user;
            assertEquals(AccountType.CUSTOMER, customer.getAccountType());
            assertTrue(
                    "Trần Thị Khách".equals(customer.getFullName())
                            || customer.getFullName().contains("Mai Anh"),
                    "Full name should match seeded customer: " + customer.getFullName()
            );
            assertTrue(
                    "khachhang@gmail.com".equals(customer.getEmail())
                            || customer.getEmail().contains("maianh.nguyen99@gmail.com"),
                    "Email should match seeded customer: " + customer.getEmail()
            );

            List<Address> addresses = customer.getAddresses();
            assertFalse(addresses.isEmpty(), "Customer KH01 should have seeded addresses");
            assertTrue(addresses.stream().anyMatch(Address::isDefaultAddress));
        }
    }

    @Test
    @DisplayName("Employee entity can be loaded via User hierarchy with discriminator EMPLOYEE")
    void canLoadEmployeeAccount() {
        try (EntityManager em = emf.createEntityManager()) {
            User user = em.find(User.class, "NV01");
            assertNotNull(user, "User NV01 should exist in seeded database");
            assertInstanceOf(Employee.class, user);

            Employee employee = (Employee) user;
            assertEquals(AccountType.EMPLOYEE, employee.getAccountType());
            assertTrue(
                    "Nguyễn Văn Quản Trị".equals(employee.getFullName())
                            || employee.getFullName().contains("Quản Trị"),
                    "Full name should match seeded employee: " + employee.getFullName()
            );
            assertTrue(
                    "admin@shop.vn".equals(employee.getEmail())
                            || "admin@crave.vn".equals(employee.getEmail()),
                    "Email should match seeded employee: " + employee.getEmail()
            );
            assertEquals(EmployeeRole.ADMIN, employee.getRole());
            assertEquals(EmployeeStatus.WORKING, employee.getStatus());
        }
    }

    @Test
    @DisplayName("Polymorphic query loads both Customer and Employee without discriminator error")
    void polymorphicQuerySucceeds() {
        try (EntityManager em = emf.createEntityManager()) {
            List<User> users = em.createQuery(
                            "SELECT u FROM User u WHERE u.id IN ('KH01', 'NV01') ORDER BY u.id", User.class)
                    .getResultList();

            assertEquals(2, users.size());
            assertInstanceOf(Customer.class, users.get(0));
            assertInstanceOf(Employee.class, users.get(1));
        }
    }

    @Test
    @DisplayName("Address can be loaded directly and links to Customer")
    void canLoadDeliveryAddress() {
        try (EntityManager em = emf.createEntityManager()) {
            Address address = em.find(Address.class, "DC01");
            assertNotNull(address);
            assertTrue(
                    "12 Nguyễn Trãi, Q.1, TP.HCM".equals(address.getAddressLine())
                            || address.getAddressLine().contains("Lê Lợi"),
                    "Address line should match seeded DC01: " + address.getAddressLine()
            );
            assertTrue(address.isDefaultAddress());
            assertNotNull(address.getCustomer());
            assertEquals("KH01", address.getCustomer().getId());
        }
    }
}
