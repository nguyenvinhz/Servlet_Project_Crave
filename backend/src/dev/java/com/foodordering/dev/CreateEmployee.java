package com.foodordering.dev;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.bean.EmployeeCreateForm;
import com.foodordering.model.Employee;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.util.PasswordHasher;

import com.foodordering.validator.AdminAccountValidator;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/** Local operator tool; never exposed through a public registration endpoint. */
public final class CreateEmployee {
    private CreateEmployee() {
    }

    public static void main(String[] args) {
        if (args.length == 5 && "--encoded-args".equals(args[0])) {
            String[] decoded = new String[4];
            for (int index = 0; index < 4; index++)
                decoded[index] = new String(Base64.getDecoder().decode(args[index + 1]), StandardCharsets.UTF_8);
            args = decoded;
        }
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Cách dùng: CreateEmployee email họ-tên số-điện-thoại [chức-vụ]");
        }
        String email = args[0].strip().toLowerCase(Locale.ROOT);
        String fullName = args[1].strip();
        String phone = args[2].strip();
        EmployeeRole role = args.length == 4 ? EmployeeRole.valueOf(args[3]) : EmployeeRole.ADMIN;
        String password = System.getenv("CRAVE_ADMIN_PASSWORD");
        if (password == null && System.console() != null) {
            char[] entered = System.console().readPassword("Mật khẩu (8–72 ký tự): ");
            if (entered != null) {
                password = new String(entered);
                Arrays.fill(entered, '\0');
            }
        }
        EmployeeCreateForm employee = AdminAccountValidator.validateEmployee(
                new EmployeeCreateForm(fullName, email, phone, password, role, null, LocalDate.now()));
        String hash = new PasswordHasher().hash(employee.password());
        try (var entityManager = DatabaseConfig.getEntityManagerFactory().createEntityManager()) {
            var transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                long existing = entityManager.createQuery(
                                "select count(u) from User u where u.email = :email or u.phone = :phone", Long.class)
                        .setParameter("email", employee.email()).setParameter("phone", employee.phone()).getSingleResult();
                if (existing != 0) {
                    throw new IllegalArgumentException("Email hoặc số điện thoại đã tồn tại. Không thay đổi tài khoản cũ.");
                }
                String id = "NV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
                entityManager.persist(new Employee(id, employee.fullName(), employee.email(), employee.phone(), hash,
                        employee.role(), employee.address(), employee.hireDate()));
                transaction.commit();
                System.out.println("Đã tạo nhân viên " + id + " · " + email + " · " + role);
            } catch (RuntimeException exception) {
                if (transaction.isActive()) transaction.rollback();
                throw exception;
            }
        } finally {
            DatabaseConfig.close();
        }
    }

}
