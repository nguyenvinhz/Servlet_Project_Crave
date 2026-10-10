package com.foodordering.service;

import com.foodordering.dto.AddressResponse;
import com.foodordering.dto.AddressWriteRequest;
import com.foodordering.dto.LoginRequest;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.ProfileUpdateRequest;
import com.foodordering.dto.RegisterRequest;
import com.foodordering.entity.Address;
import com.foodordering.entity.Customer;
import com.foodordering.entity.Employee;
import com.foodordering.entity.User;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.exception.AccountException;
import com.foodordering.repository.AccountRepository;
import com.foodordering.utils.JsonProvider;
import com.foodordering.utils.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AccountServiceTest {

    private MemoryRepository repository;
    private AccountService service;

    @BeforeEach
    void setUp() {
        repository = new MemoryRepository();
        service = new AccountService(repository);
        repository.users.put("KH01", new Customer("KH01", "Khách hàng", "customer@example.com",
                "0901234567", "hashed_customer123"));
        repository.users.put("KH02", new Customer("KH02", "Khách khác", "other@example.com",
                "0907654321", "hashed_customer456"));
    }

    @Test
    void registrationNormalizesContactsHashesPasswordAndSupportsLoginWithoutLeakingSecrets() throws Exception {
        ProfileResponse registered = service.register(new RegisterRequest("  Nguyễn Quang Vinh  ",
                " VINH@Example.com ", " 0912345678 ", "Secret#123", "Secret#123"));

        assertEquals("Nguyễn Quang Vinh", registered.fullName());
        assertEquals("vinh@example.com", registered.email());
        assertEquals("0912345678", registered.phone());
        assertEquals(AccountType.CUSTOMER, registered.accountType());
        assertNull(registered.role());
        assertTrue(registered.id().startsWith("KH"));
        assertEquals(10, registered.id().length());
        assertTrue(repository.users.get(registered.id()).getPasswordHash().startsWith("pbkdf2_sha256$600000$"));
        assertEquals(registered, service.login(new LoginRequest("VINH@example.com", "Secret#123", true)));
        String json = JsonProvider.objectMapper().writeValueAsString(registered);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("Secret#123"));
        assertFalse(json.contains("role"));
        assertEquals(401, assertThrows(AccountException.class,
                () -> service.login(new LoginRequest(registered.email(), "Wrong#123", false))).getStatusCode());
        ((Customer) repository.users.get(registered.id())).setStatus(CustomerStatus.LOCKED);
        assertEquals(401, assertThrows(AccountException.class,
                () -> service.login(new LoginRequest(registered.email(), "Secret#123", false))).getStatusCode());
    }

    @Test
    void registrationReportsAllInvalidFieldsBeforePersistence() {
        AccountException exception = assertThrows(AccountException.class, () -> service.register(
                new RegisterRequest("A", "invalid", "12+34", "short", "different")));

        assertEquals(400, exception.getStatusCode());
        assertTrue(exception.getDetails().keySet().containsAll(
                List.of("fullName", "email", "phone", "password", "confirmPassword")));
        assertEquals(2, repository.users.size());
    }

    @Test
    void registrationAndLoginRejectMalformedPasswordTextBeforeHashingOrVerification() {
        PasswordHasher hasher = mock(PasswordHasher.class);
        AccountService guarded = new AccountService(repository, hasher);

        for (String invalid : List.of("Password#123" + '\0', "Pass" + '\0' + "word#123",
                "Password" + (char) 0xD800 + "42", "Password" + (char) 0xD801 + "42",
                "Password" + (char) 0xDC00 + "42")) {
            AccountException register = assertThrows(AccountException.class, () -> guarded.register(
                    new RegisterRequest("Audit User", "audit@example.com", "0912345678", invalid, invalid)));
            assertEquals(400, register.getStatusCode());
            assertEquals("VALIDATION_ERROR", register.getErrorCode());
            assertTrue(register.getDetails().keySet().containsAll(List.of("password", "confirmPassword")));

            AccountException login = assertThrows(AccountException.class, () -> guarded.login(
                    new LoginRequest("customer@example.com", invalid, false)));
            assertEquals(400, login.getStatusCode());
            assertEquals("VALIDATION_ERROR", login.getErrorCode());
            assertTrue(login.getDetails().containsKey("password"));
        }

        verifyNoInteractions(hasher);
        assertEquals(2, repository.users.size());
    }

    @Test
    void confirmationPasswordIsValidatedBeforeRegistration() {
        PasswordHasher hasher = mock(PasswordHasher.class);
        AccountService guarded = new AccountService(repository, hasher);
        AccountException failure = assertThrows(AccountException.class, () -> guarded.register(
                new RegisterRequest("Audit User", "audit@example.com", "0912345678",
                        "Password?42", "Password" + (char) 0xD800 + "42")));

        assertEquals(400, failure.getStatusCode());
        assertTrue(failure.getDetails().containsKey("confirmPassword"));
        assertFalse(failure.getDetails().containsKey("password"));
        verifyNoInteractions(hasher);
        assertEquals(2, repository.users.size());
    }

    @Test
    void registrationAndLoginPreserveValidVietnameseAndEmojiPasswords() {
        String password = "MậtKhẩu#🔒123";
        ProfileResponse registered = service.register(new RegisterRequest("Audit User", "audit@example.com",
                "0912345678", password, password));

        assertEquals(registered, service.login(new LoginRequest(registered.email(), password, false)));
        AccountException wrong = assertThrows(AccountException.class, () -> service.login(
                new LoginRequest(registered.email(), "MậtKhẩu#🔑123", false)));
        assertEquals(401, wrong.getStatusCode());
        assertEquals("INVALID_CREDENTIALS", wrong.getErrorCode());
    }

    @Test
    void registrationChecksEmailAndPhoneInOneSharedCustomerEmployeeNamespace() {
        repository.users.put("NV01", employee(EmployeeStatus.WORKING));
        AccountException exception = assertThrows(AccountException.class, () -> service.register(
                new RegisterRequest("New User", "ADMIN@example.com", "0901234567", "Secret#123", "Secret#123")));

        assertEquals(409, exception.getStatusCode());
        assertTrue(exception.getDetails().containsKey("email"));
        assertTrue(exception.getDetails().containsKey("phone"));
    }

    @Test
    void rejectsSeedPlaceholderAndUnknownLoginWithSameCredentialsError() {
        AccountException placeholder = assertThrows(AccountException.class,
                () -> service.login(new LoginRequest("customer@example.com", "customer123", false)));
        AccountException missing = assertThrows(AccountException.class,
                () -> service.login(new LoginRequest("nobody@example.com", "customer123", false)));

        assertEquals(401, placeholder.getStatusCode());
        assertEquals("INVALID_CREDENTIALS", placeholder.getErrorCode());
        assertEquals(placeholder.getMessage(), missing.getMessage());
    }

    @Test
    void unknownAndSeedAccountsBothInvokePasswordVerificationBeforeCredentialsRejection() {
        PasswordHasher hasher = mock(PasswordHasher.class);
        AccountService instrumented = new AccountService(repository, hasher);

        assertThrows(AccountException.class,
                () -> instrumented.login(new LoginRequest("nobody@example.com", "customer123", false)));
        assertThrows(AccountException.class,
                () -> instrumented.login(new LoginRequest("customer@example.com", "customer123", false)));

        verify(hasher).verify("customer123", null);
        verify(hasher).verify("customer123", "hashed_customer123");
    }

    @Test
    void profilesRevalidateCustomerAndEmployeeStatus() {
        Customer customer = (Customer) repository.users.get("KH01");
        customer.setStatus(CustomerStatus.LOCKED);
        assertEquals(401, assertThrows(AccountException.class, () -> service.getProfile("KH01")).getStatusCode());
        repository.users.put("NV01", employee(EmployeeStatus.WORKING));
        ProfileResponse employee = service.getProfile("NV01");
        assertEquals(AccountType.EMPLOYEE, employee.accountType());
        assertEquals(EmployeeRole.ADMIN, employee.role());
        ((Employee) repository.users.get("NV01")).setStatus(EmployeeStatus.ON_LEAVE);
        assertEquals(401, assertThrows(AccountException.class, () -> service.getProfile("NV01")).getStatusCode());
        assertEquals(401, assertThrows(AccountException.class, () -> service.getProfile(null)).getStatusCode());
    }

    @Test
    void profileUpdateAllowsOwnContactsAndRejectsContactsOwnedByAnotherAccount() {
        ProfileResponse updated = service.updateProfile("KH01",
                new ProfileUpdateRequest("Tên mới", "CUSTOMER@example.com", "0901234567"));
        assertEquals("Tên mới", updated.fullName());
        assertTrue(repository.locked);
        AccountException exception = assertThrows(AccountException.class, () -> service.updateProfile("KH01",
                new ProfileUpdateRequest("Tên mới", "other@example.com", "0907654321")));
        assertEquals(409, exception.getStatusCode());
        assertEquals(2, exception.getDetails().size());
        assertEquals("customer@example.com", repository.users.get("KH01").getEmail());
    }

    @Test
    void employeeCannotUpdateCustomerProfileOrManageCustomerAddresses() {
        repository.users.put("NV01", employee(EmployeeStatus.WORKING));
        assertEquals(403, assertThrows(AccountException.class, () -> service.updateProfile("NV01",
                new ProfileUpdateRequest("Admin User", "admin@example.com", "0991234567"))).getStatusCode());
        assertEquals(403, assertThrows(AccountException.class, () -> service.listAddresses("NV01")).getStatusCode());
    }

    @Test
    void firstAddressAutomaticallyBecomesDefaultAndSwitchingDefaultFlushesSafely() {
        AddressResponse first = service.createAddress("KH01", writeAddress(" First address ", false));
        assertTrue(first.isDefault());
        assertEquals("First address", first.addressLine());
        AddressResponse second = service.createAddress("KH01", writeAddress("Second address", true));

        assertTrue(second.isDefault());
        assertFalse(service.getAddress("KH01", first.id()).isDefault());
        assertEquals(1, service.listAddresses("KH01").stream().filter(AddressResponse::isDefault).count());
        assertTrue(repository.locked);
        assertTrue(repository.flushes >= 4);
    }

    @Test
    void clearingDefaultPromotesAnotherAddressAndSingletonRetainsDefault() {
        AddressResponse first = service.createAddress("KH01", writeAddress("First address", false));
        service.updateAddress("KH01", first.id(), writeAddress("First revised", false));
        assertTrue(service.getAddress("KH01", first.id()).isDefault());
        AddressResponse second = service.createAddress("KH01", writeAddress("Second address", false));
        AddressResponse updated = service.updateAddress("KH01", first.id(), writeAddress("First revised", false));

        assertFalse(updated.isDefault());
        assertTrue(service.getAddress("KH01", second.id()).isDefault());
        assertEquals("First revised", updated.addressLine());
    }

    @Test
    void deletionPromotesRemainingAddressAndCanDeleteLastAddress() {
        AddressResponse first = service.createAddress("KH01", writeAddress("First address", true));
        AddressResponse second = service.createAddress("KH01", writeAddress("Second address", false));
        service.deleteAddress("KH01", first.id());

        assertTrue(service.getAddress("KH01", second.id()).isDefault());
        service.deleteAddress("KH01", second.id());
        assertTrue(service.listAddresses("KH01").isEmpty());
    }

    @Test
    void ownershipHidesAnotherCustomersAddressOnReadUpdateAndDelete() {
        AddressResponse owned = service.createAddress("KH01", writeAddress("Private address", true));

        assertTrue(service.listAddresses("KH02").isEmpty());
        assertEquals(404, assertThrows(AccountException.class,
                () -> service.getAddress("KH02", owned.id())).getStatusCode());
        assertEquals(404, assertThrows(AccountException.class,
                () -> service.updateAddress("KH02", owned.id(), writeAddress("Hijacked", true))).getStatusCode());
        assertEquals(404, assertThrows(AccountException.class,
                () -> service.deleteAddress("KH02", owned.id())).getStatusCode());
        assertEquals("Private address", service.getAddress("KH01", owned.id()).addressLine());
    }

    @Test
    void addressValidationReportsRequiredFieldsAndLimits() {
        AccountException exception = assertThrows(AccountException.class,
                () -> service.createAddress("KH01", new AddressWriteRequest("  ", "a".repeat(256), null)));

        assertEquals(400, exception.getStatusCode());
        assertTrue(exception.getDetails().keySet().containsAll(List.of("addressLine", "note", "isDefault")));
        assertTrue(repository.addresses.isEmpty());
    }

    private static AddressWriteRequest writeAddress(String addressLine, boolean isDefault) {
        return new AddressWriteRequest(addressLine, null, isDefault);
    }

    private static Employee employee(EmployeeStatus status) {
        return new Employee("NV01", "Admin User", "admin@example.com", "0991234567", "hashed_admin123",
                EmployeeRole.ADMIN, null, LocalDate.of(2026, 1, 1), status);
    }

    private static class MemoryRepository implements AccountRepository, AccountRepository.Session {
        private final Map<String, User> users = new LinkedHashMap<>();
        private final Map<String, Address> addresses = new LinkedHashMap<>();
        private boolean locked;
        private int flushes;

        @Override
        public <T> T read(Function<Session, T> operation) {
            return operation.apply(this);
        }

        @Override
        public <T> T write(Function<Session, T> operation) {
            return operation.apply(this);
        }

        @Override
        public Optional<User> findUser(String id) {
            return Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return users.values().stream().filter(user -> user.getEmail().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public Optional<Customer> findCustomer(String id, boolean lock) {
            locked |= lock;
            return Optional.ofNullable(users.get(id)).filter(Customer.class::isInstance).map(Customer.class::cast);
        }

        @Override
        public Map<String, String> findContactConflicts(String email, String phone, String excludedId) {
            Map<String, String> conflicts = new LinkedHashMap<>();
            users.values().stream().filter(user -> !user.getId().equals(excludedId)).forEach(user -> {
                if (user.getEmail().equalsIgnoreCase(email)) conflicts.put("email", "Email đã được sử dụng.");
                if (user.getPhone().equals(phone)) conflicts.put("phone", "Số điện thoại đã được sử dụng.");
            });
            return conflicts;
        }

        @Override
        public boolean userIdExists(String id) {
            return users.containsKey(id);
        }

        @Override
        public boolean addressIdExists(String id) {
            return addresses.containsKey(id);
        }

        @Override
        public List<Address> findAddresses(String customerId) {
            return new ArrayList<>(addresses.values().stream()
                    .filter(address -> address.getCustomer().getId().equals(customerId)).toList());
        }

        @Override
        public Optional<Address> findAddress(String customerId, String addressId) {
            return Optional.ofNullable(addresses.get(addressId))
                    .filter(address -> address.getCustomer().getId().equals(customerId));
        }

        @Override
        public void persistCustomer(Customer customer) {
            users.put(customer.getId(), customer);
        }

        @Override
        public void persistAddress(Address address) {
            addresses.put(address.getId(), address);
        }

        @Override
        public void removeAddress(Address address) {
            addresses.remove(address.getId());
        }

        @Override
        public void flush() {
            flushes++;
            for (String customerId : users.keySet()) {
                assertTrue(findAddresses(customerId).stream().filter(Address::isDefaultAddress).count() <= 1,
                        "Every flush must respect the database unique default address constraint");
            }
        }

        @Override
        public void refresh(Address address) {
            assertNotNull(addresses.get(address.getId()));
        }
    }
}
