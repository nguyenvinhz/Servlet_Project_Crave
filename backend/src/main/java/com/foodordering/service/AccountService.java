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
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.exception.AccountException;
import com.foodordering.repository.AccountRepository;
import com.foodordering.repository.AccountRepository.Session;
import com.foodordering.repository.JpaAccountRepository;
import com.foodordering.utils.PasswordHasher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class AccountService {

    private final AccountRepository repository;
    private final PasswordHasher passwordHasher;

    public AccountService() {
        this(new JpaAccountRepository(), new PasswordHasher());
    }

    public AccountService(AccountRepository repository) {
        this(repository, new PasswordHasher());
    }

    public AccountService(AccountRepository repository, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
    }

    public ProfileResponse register(RegisterRequest request) {
        requireRequest(request);
        Map<String, String> errors = new LinkedHashMap<>();
        Contact contact = validateContact(request.fullName(), request.email(), request.phone(), errors);
        validatePassword(request.password(), "password", errors);
        validatePassword(request.confirmPassword(), "confirmPassword", errors);
        if (request.password() != null && !request.password().equals(request.confirmPassword())) {
            errors.put("confirmPassword", "Mật khẩu xác nhận không khớp.");
        }
        rejectInvalid(errors);
        String hash = passwordHasher.hash(request.password());
        return repository.write(session -> {
            rejectConflicts(session, contact, null);
            Customer customer = new Customer(newId("KH", session::userIdExists), contact.fullName(),
                    contact.email(), contact.phone(), hash);
            session.persistCustomer(customer);
            session.flush();
            return profile(customer);
        });
    }

    public ProfileResponse login(LoginRequest request) {
        requireRequest(request);
        Map<String, String> errors = new LinkedHashMap<>();
        String email = normalizeEmail(request.email());
        validateEmail(email, errors);
        validatePassword(request.password(), "password", errors);
        rejectInvalid(errors);
        return repository.read(session -> {
            User user = session.findByEmail(email).orElse(null);
            boolean verified = passwordHasher.verify(request.password(), user == null ? null : user.getPasswordHash());
            if (user == null || !verified) {
                throw invalidCredentials();
            }
            requireActive(user);
            return profile(user);
        });
    }

    /** Revalidates account status for every authenticated request, including employee sessions. */
    public ProfileResponse getProfile(String userId) {
        requireSessionId(userId);
        return repository.read(session -> {
            User user = session.findUser(userId).orElseThrow(AccountService::unauthorized);
            requireActive(user);
            return profile(user);
        });
    }

    public ProfileResponse updateProfile(String customerId, ProfileUpdateRequest request) {
        requireRequest(request);
        Map<String, String> errors = new LinkedHashMap<>();
        Contact contact = validateContact(request.fullName(), request.email(), request.phone(), errors);
        rejectInvalid(errors);
        return repository.write(session -> {
            Customer customer = requireCustomer(session, customerId, true);
            rejectConflicts(session, contact, customerId);
            customer.setFullName(contact.fullName());
            customer.setEmail(contact.email());
            customer.setPhone(contact.phone());
            session.flush();
            return profile(customer);
        });
    }

    public List<AddressResponse> listAddresses(String customerId) {
        return repository.read(session -> {
            requireCustomer(session, customerId, false);
            return session.findAddresses(customerId).stream().map(AccountService::address).toList();
        });
    }

    public AddressResponse getAddress(String customerId, String addressId) {
        return repository.read(session -> {
            requireCustomer(session, customerId, false);
            return address(requireAddress(session, customerId, addressId));
        });
    }

    public AddressResponse createAddress(String customerId, AddressWriteRequest request) {
        AddressWriteRequest validated = validateAddress(request);
        return repository.write(session -> {
            Customer customer = requireCustomer(session, customerId, true);
            List<Address> addresses = new ArrayList<>(session.findAddresses(customerId));
            Address created = new Address(newId("DC", session::addressIdExists), validated.addressLine(),
                    validated.note(), false);
            created.setCustomer(customer);
            Address selected = Boolean.TRUE.equals(validated.isDefault()) || addresses.isEmpty()
                    ? created : currentDefaultOrFirst(addresses);
            addresses.add(created);
            // The database has a unique default_customer_id index. Clear and flush before insert/switch.
            selectDefault(session, addresses, selected);
            session.persistAddress(created);
            session.flush();
            session.refresh(created);
            return address(created);
        });
    }

    public AddressResponse updateAddress(String customerId, String addressId, AddressWriteRequest request) {
        AddressWriteRequest validated = validateAddress(request);
        return repository.write(session -> {
            requireCustomer(session, customerId, true);
            Address updated = requireAddress(session, customerId, addressId);
            List<Address> addresses = session.findAddresses(customerId);
            Address selected;
            if (Boolean.TRUE.equals(validated.isDefault())) {
                selected = updated;
            } else if (updated.isDefaultAddress()) {
                selected = addresses.stream().filter(item -> !item.getId().equals(addressId))
                        .findFirst().orElse(updated);
            } else {
                selected = currentDefaultOrFirst(addresses);
            }
            selectDefault(session, addresses, selected);
            updated.setAddressLine(validated.addressLine());
            updated.setNote(validated.note());
            session.flush();
            session.refresh(updated);
            return address(updated);
        });
    }

    public void deleteAddress(String customerId, String addressId) {
        repository.write(session -> {
            requireCustomer(session, customerId, true);
            Address deleted = requireAddress(session, customerId, addressId);
            List<Address> remaining = new ArrayList<>(session.findAddresses(customerId));
            remaining.removeIf(item -> item.getId().equals(addressId));
            // Orders store their own address snapshot, which must remain unchanged.
            session.removeAddress(deleted);
            session.flush();
            if (!remaining.isEmpty()) {
                selectDefault(session, remaining, currentDefaultOrFirst(remaining));
                session.flush();
            }
            return null;
        });
    }

    private static void selectDefault(Session session, List<Address> addresses, Address selected) {
        for (Address item : addresses) {
            if (item != selected && item.isDefaultAddress()) {
                item.setDefaultAddress(false);
            }
        }
        session.flush();
        selected.setDefaultAddress(true);
    }

    private static Address currentDefaultOrFirst(List<Address> addresses) {
        return addresses.stream().filter(Address::isDefaultAddress).findFirst().orElse(addresses.get(0));
    }

    private static Customer requireCustomer(Session session, String customerId, boolean lock) {
        requireSessionId(customerId);
        Customer customer = session.findCustomer(customerId, lock).orElseGet(() -> {
            User user = session.findUser(customerId).orElseThrow(AccountService::unauthorized);
            requireActive(user);
            throw new AccountException("FORBIDDEN", "Chức năng này dành cho khách hàng.", 403);
        });
        requireActive(customer);
        return customer;
    }

    private static Address requireAddress(Session session, String customerId, String addressId) {
        if (addressId == null || addressId.isBlank() || addressId.length() > 10) {
            throw addressNotFound();
        }
        return session.findAddress(customerId, addressId).orElseThrow(AccountService::addressNotFound);
    }

    private static void requireActive(User user) {
        if ((user instanceof Customer customer && customer.getStatus() != CustomerStatus.ACTIVE)
                || (user instanceof Employee employee && employee.getStatus() != EmployeeStatus.WORKING)) {
            throw new AccountException("ACCOUNT_INACTIVE", "Tài khoản hiện không hoạt động.", 401);
        }
    }

    private static void requireSessionId(String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 10) {
            throw unauthorized();
        }
    }

    private static Contact validateContact(String fullName, String email, String phone,
                                           Map<String, String> errors) {
        String name = trim(fullName);
        String normalizedEmail = normalizeEmail(email);
        String normalizedPhone = trim(phone);
        if (name.length() < 2 || name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) {
            errors.put("fullName", "Họ tên phải có từ 2 đến 100 ký tự.");
        }
        validateEmail(normalizedEmail, errors);
        if (normalizedPhone.length() < 9 || normalizedPhone.length() > 15
                || !normalizedPhone.matches("\\+?[0-9]{8,15}")) {
            errors.put("phone", "Số điện thoại phải có từ 9 đến 15 ký tự, gồm chữ số và dấu + ở đầu.");
        }
        return new Contact(name, normalizedEmail, normalizedPhone);
    }

    private static void validateEmail(String email, Map<String, String> errors) {
        if (email.length() > 100 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            errors.put("email", "Email không đúng định dạng hoặc vượt quá 100 ký tự.");
        }
    }

    private static void validatePassword(String password, String field, Map<String, String> errors) {
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 72) {
            errors.put(field, "Mật khẩu phải có từ 8 đến 72 ký tự.");
        } else if (!PasswordHasher.isValidPasswordText(password)) {
            errors.put(field, "Mật khẩu chứa ký tự không hợp lệ.");
        }
    }

    private static AddressWriteRequest validateAddress(AddressWriteRequest request) {
        requireRequest(request);
        Map<String, String> errors = new LinkedHashMap<>();
        String addressLine = trim(request.addressLine());
        String note = request.note() == null ? null : request.note().strip();
        if (addressLine.isEmpty() || addressLine.length() > 255) {
            errors.put("addressLine", "Địa chỉ không được để trống và không vượt quá 255 ký tự.");
        }
        if (note != null && note.length() > 255) {
            errors.put("note", "Ghi chú không vượt quá 255 ký tự.");
        }
        if (request.isDefault() == null) {
            errors.put("isDefault", "Vui lòng chọn trạng thái địa chỉ mặc định.");
        }
        rejectInvalid(errors);
        return new AddressWriteRequest(addressLine, note, request.isDefault());
    }

    private static void rejectConflicts(Session session, Contact contact, String excludedId) {
        Map<String, String> conflicts = session.findContactConflicts(contact.email(), contact.phone(), excludedId);
        if (!conflicts.isEmpty()) {
            throw new AccountException("ACCOUNT_CONFLICT", "Email hoặc số điện thoại đã được sử dụng.", 409,
                    conflicts);
        }
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new AccountException("VALIDATION_ERROR", "Dữ liệu không hợp lệ.", 400,
                    Map.of("request", "Vui lòng cung cấp dữ liệu."));
        }
    }

    private static void rejectInvalid(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new AccountException("VALIDATION_ERROR", "Dữ liệu không hợp lệ.", 400, errors);
        }
    }

    private static String normalizeEmail(String email) {
        return trim(email).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static String newId(String prefix, Predicate<String> exists) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                    .toUpperCase(Locale.ROOT);
            if (!exists.test(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Unable to generate a unique account resource id.");
    }

    private static ProfileResponse profile(User user) {
        return new ProfileResponse(user.getId(), user instanceof Employee ? AccountType.EMPLOYEE : AccountType.CUSTOMER,
                user.getFullName(), user.getEmail(), user.getPhone(),
                user instanceof Employee employee ? employee.getRole() : null);
    }

    private static AddressResponse address(Address address) {
        return new AddressResponse(address.getId(), address.getAddressLine(), address.getNote(),
                address.isDefaultAddress(), address.getCreatedAt(), address.getUpdatedAt());
    }

    private static AccountException invalidCredentials() {
        return new AccountException("INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng.", 401);
    }

    private static AccountException unauthorized() {
        return new AccountException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.", 401);
    }

    private static AccountException addressNotFound() {
        return new AccountException("NOT_FOUND", "Không tìm thấy địa chỉ.", 404);
    }

    private record Contact(String fullName, String email, String phone) {
    }
}
