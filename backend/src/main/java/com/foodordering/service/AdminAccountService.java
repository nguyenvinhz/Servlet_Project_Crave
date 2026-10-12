package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.dto.AdminCustomerResponse;
import com.foodordering.dto.AdminEmployeeResponse;
import com.foodordering.dto.EmployeeCreateRequest;
import com.foodordering.entity.Customer;
import com.foodordering.entity.Employee;
import com.foodordering.entity.User;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.exception.ConflictException;
import com.foodordering.exception.ForbiddenException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.exception.ValidationException;
import com.foodordering.utils.PasswordHasher;
import com.foodordering.validator.AdminAccountValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/** Staff administration returns explicit DTOs so account hashes never leave this layer. */
public class AdminAccountService {
    private final EntityManagerFactory emf;
    private final PasswordHasher passwordHasher;

    public AdminAccountService() {
        this(DatabaseConfig.getEntityManagerFactory(), new PasswordHasher());
    }

    public AdminAccountService(EntityManagerFactory emf) {
        this(emf, new PasswordHasher());
    }

    public AdminAccountService(EntityManagerFactory emf, PasswordHasher passwordHasher) {
        this.emf = emf;
        this.passwordHasher = passwordHasher;
    }

    public List<AdminCustomerResponse> listCustomers() {
        try (EntityManager em = emf.createEntityManager()) {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT c.customer_id, u.full_name, u.email, u.phone, c.status, c.registered_at,
                           COUNT(o.order_id), COALESCE(SUM(CASE WHEN o.status = 'COMPLETED' THEN o.total_amount ELSE 0 END), 0)
                    FROM customer c JOIN user_account u ON u.user_id = c.customer_id
                    LEFT JOIN customer_order o ON o.customer_id = c.customer_id
                    GROUP BY c.customer_id, u.full_name, u.email, u.phone, c.status, c.registered_at
                    ORDER BY c.registered_at DESC, c.customer_id
                    """).getResultList();
            return rows.stream().map(AdminAccountService::customer).toList();
        }
    }

    public List<AdminEmployeeResponse> listEmployees() {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("SELECT e FROM Employee e ORDER BY e.hireDate DESC, e.id", Employee.class)
                    .getResultList().stream().map(AdminAccountService::employee).toList();
        }
    }

    public AdminCustomerResponse updateCustomerStatus(String actorId, String id, CustomerStatus status) {
        if (status == null) throw new ValidationException("Vui lòng chọn trạng thái khách hàng.");
        return write(em -> {
            requireManager(lockEmployees(em), actorId);
            Customer target = em.find(Customer.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (target == null) throw new ResourceNotFoundException("Không tìm thấy khách hàng.");
            target.setStatus(status);
            em.flush();
            Object[] stats = (Object[]) em.createNativeQuery("""
                    SELECT COUNT(order_id), COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN total_amount ELSE 0 END), 0)
                    FROM customer_order WHERE customer_id = :id
                    """).setParameter("id", id).getSingleResult();
            return new AdminCustomerResponse(target.getId(), target.getFullName(), target.getEmail(), target.getPhone(),
                    target.getStatus(), target.getRegisteredAt(), ((Number) stats[0]).longValue(), decimal(stats[1]));
        });
    }

    public AdminEmployeeResponse updateEmployeeStatus(String actorId, String id, EmployeeStatus status) {
        if (status == null) throw new ValidationException("Vui lòng chọn trạng thái nhân viên.");
        return write(em -> {
            List<Employee> employees = lockEmployees(em);
            Employee actor = requireManager(employees, actorId);
            Employee target = requireEmployee(employees, id);
            requireAdminPermission(actor, target.getRole());
            if (actorId.equals(id) && status != EmployeeStatus.WORKING) {
                throw new ConflictException("Bạn không thể khóa tài khoản của chính mình.");
            }
            if (target.getRole() == EmployeeRole.ADMIN && target.getStatus() == EmployeeStatus.WORKING
                    && status != EmployeeStatus.WORKING) {
                requireAnotherAdmin(employees, id);
            }
            target.setStatus(status);
            em.flush();
            return employee(target);
        });
    }

    public AdminEmployeeResponse updateEmployeeRole(String actorId, String id, EmployeeRole role) {
        if (role == null) throw new ValidationException("Vui lòng chọn chức vụ nhân viên.");
        return write(em -> {
            List<Employee> employees = lockEmployees(em);
            Employee actor = requireManager(employees, actorId);
            Employee target = requireEmployee(employees, id);
            requireAdminPermission(actor, target.getRole());
            requireAdminPermission(actor, role);
            if (actorId.equals(id) && role != target.getRole()) {
                throw new ConflictException("Bạn không thể thay đổi chức vụ của chính mình.");
            }
            if (target.getRole() == EmployeeRole.ADMIN && target.getStatus() == EmployeeStatus.WORKING
                    && role != EmployeeRole.ADMIN) {
                requireAnotherAdmin(employees, id);
            }
            target.setRole(role);
            em.flush();
            return employee(target);
        });
    }

    public AdminEmployeeResponse createEmployee(String actorId, EmployeeCreateRequest request) {
        EmployeeCreateRequest validated = AdminAccountValidator.validateEmployee(request);
        String hash = passwordHasher.hash(validated.password());
        return write(em -> {
            Employee actor = requireManager(lockEmployees(em), actorId);
            requireAdminPermission(actor, validated.role());
            Long duplicates = em.createQuery("SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = :email OR u.phone = :phone", Long.class)
                    .setParameter("email", validated.email()).setParameter("phone", validated.phone()).getSingleResult();
            if (duplicates > 0) throw new ConflictException("Email hoặc số điện thoại đã được sử dụng.");
            String id;
            do {
                id = "NV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            } while (em.find(User.class, id) != null);
            Employee employee = new Employee(id, validated.fullName(), validated.email(), validated.phone(), hash,
                    validated.role(), validated.address(), validated.hireDate());
            em.persist(employee);
            em.flush();
            return employee(employee);
        });
    }

    /** Consistent lock order serializes role/status changes, including the last-admin check. */
    private static List<Employee> lockEmployees(EntityManager em) {
        return em.createQuery("SELECT e FROM Employee e ORDER BY e.id", Employee.class)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
    }

    private static Employee requireManager(List<Employee> employees, String id) {
        Employee actor = employees.stream().filter(e -> e.getId().equals(id)).findFirst().orElse(null);
        if (actor == null || actor.getStatus() != EmployeeStatus.WORKING
                || (actor.getRole() != EmployeeRole.ADMIN && actor.getRole() != EmployeeRole.HR_MANAGER)) {
            throw new ForbiddenException("Bạn không có quyền quản lý tài khoản.");
        }
        return actor;
    }

    private static Employee requireEmployee(List<Employee> employees, String id) {
        return employees.stream().filter(e -> e.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên."));
    }

    private static void requireAdminPermission(Employee actor, EmployeeRole role) {
        if (role == EmployeeRole.ADMIN && actor.getRole() != EmployeeRole.ADMIN) {
            throw new ForbiddenException("Chỉ quản trị viên được quản lý tài khoản quản trị viên.");
        }
    }

    private static void requireAnotherAdmin(List<Employee> employees, String excludedId) {
        boolean another = employees.stream().anyMatch(e -> !e.getId().equals(excludedId)
                && e.getRole() == EmployeeRole.ADMIN && e.getStatus() == EmployeeStatus.WORKING);
        if (!another) throw new ConflictException("Phải giữ ít nhất một quản trị viên đang làm việc.");
    }

    private <T> T write(Function<EntityManager, T> work) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                T result = work.apply(em);
                em.getTransaction().commit();
                return result;
            } catch (RuntimeException exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                if (constraintViolation(exception)) throw new ConflictException("Email hoặc số điện thoại đã được sử dụng.");
                throw exception;
            }
        }
    }

    private static boolean constraintViolation(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "23000".equals(violation.getSQLState())) return true;
        }
        return false;
    }

    private static AdminEmployeeResponse employee(Employee employee) {
        return new AdminEmployeeResponse(employee.getId(), employee.getFullName(), employee.getEmail(), employee.getPhone(),
                employee.getRole(), employee.getStatus(), employee.getAddress(), employee.getHireDate());
    }

    private static AdminCustomerResponse customer(Object[] row) {
        Object registeredAt = row[5];
        LocalDateTime registered = registeredAt instanceof Timestamp timestamp ? timestamp.toLocalDateTime()
                : (LocalDateTime) registeredAt;
        return new AdminCustomerResponse((String) row[0], (String) row[1], (String) row[2], (String) row[3],
                CustomerStatus.valueOf((String) row[4]), registered, ((Number) row[6]).longValue(), decimal(row[7]));
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
