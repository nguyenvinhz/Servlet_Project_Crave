package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Address;
import com.foodordering.entity.Customer;
import com.foodordering.entity.User;
import com.foodordering.exception.AccountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class JpaAccountRepository implements AccountRepository {

    private final Supplier<EntityManagerFactory> factory;

    public JpaAccountRepository() {
        this.factory = DatabaseConfig::getEntityManagerFactory;
    }

    public JpaAccountRepository(EntityManagerFactory emf) {
        this.factory = () -> emf;
    }

    @Override
    public <T> T read(Function<Session, T> operation) {
        try (EntityManager em = factory.get().createEntityManager()) {
            return operation.apply(new JpaSession(em));
        }
    }

    @Override
    public <T> T write(Function<Session, T> operation) {
        try (EntityManager em = factory.get().createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            try {
                transaction.begin();
                T result = operation.apply(new JpaSession(em));
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw translateConstraint(e);
            }
        }
    }

    private RuntimeException translateConstraint(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String message = String.valueOf(sql.getMessage()).toLowerCase(Locale.ROOT);
                if (sql.getErrorCode() == 1062) {
                    if (message.contains("uq_user_account_email")) {
                        return new AccountException("ACCOUNT_CONFLICT", "Email đã được sử dụng.", 409,
                                Map.of("email", "Email đã được sử dụng."));
                    }
                    if (message.contains("uq_user_account_phone")) {
                        return new AccountException("ACCOUNT_CONFLICT", "Số điện thoại đã được sử dụng.", 409,
                                Map.of("phone", "Số điện thoại đã được sử dụng."));
                    }
                    return new AccountException("RESOURCE_CONFLICT", "Dữ liệu trùng lặp, vui lòng thử lại.", 409);
                }
                if (sql.getErrorCode() == 1451) {
                    return new AccountException("ADDRESS_IN_USE", "Địa chỉ đang được sử dụng và không thể xóa.", 409);
                }
            }
        }
        return failure;
    }

    private record JpaSession(EntityManager em) implements Session {

        @Override
        public Optional<User> findUser(String id) {
            return Optional.ofNullable(em.find(User.class, id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return em.createQuery("SELECT u FROM User u WHERE LOWER(u.email) = :email", User.class)
                    .setParameter("email", email).getResultStream().findFirst();
        }

        @Override
        public Optional<Customer> findCustomer(String id, boolean lock) {
            return Optional.ofNullable(lock
                    ? em.find(Customer.class, id, LockModeType.PESSIMISTIC_WRITE)
                    : em.find(Customer.class, id));
        }

        @Override
        public Map<String, String> findContactConflicts(String email, String phone, String excludedId) {
            List<User> matches = em.createQuery("""
                            SELECT u FROM User u
                            WHERE (LOWER(u.email) = :email OR u.phone = :phone)
                            AND (:excludedId IS NULL OR u.id <> :excludedId)
                            """, User.class)
                    .setParameter("email", email).setParameter("phone", phone)
                    .setParameter("excludedId", excludedId).getResultList();
            Map<String, String> conflicts = new LinkedHashMap<>();
            for (User user : matches) {
                if (user.getEmail().equalsIgnoreCase(email)) {
                    conflicts.put("email", "Email đã được sử dụng.");
                }
                if (user.getPhone().equals(phone)) {
                    conflicts.put("phone", "Số điện thoại đã được sử dụng.");
                }
            }
            return conflicts;
        }

        @Override
        public boolean userIdExists(String id) {
            return em.find(User.class, id) != null;
        }

        @Override
        public boolean addressIdExists(String id) {
            return em.find(Address.class, id) != null;
        }

        @Override
        public List<Address> findAddresses(String customerId) {
            return em.createQuery("""
                            SELECT a FROM Address a WHERE a.customer.id = :customerId
                            ORDER BY a.createdAt ASC, a.id ASC
                            """, Address.class)
                    .setParameter("customerId", customerId).getResultList();
        }

        @Override
        public Optional<Address> findAddress(String customerId, String addressId) {
            return em.createQuery("""
                            SELECT a FROM Address a
                            WHERE a.id = :addressId AND a.customer.id = :customerId
                            """, Address.class)
                    .setParameter("customerId", customerId).setParameter("addressId", addressId)
                    .getResultStream().findFirst();
        }

        @Override
        public void persistCustomer(Customer customer) {
            em.persist(customer);
        }

        @Override
        public void persistAddress(Address address) {
            em.persist(address);
        }

        @Override
        public void removeAddress(Address address) {
            em.remove(address);
        }

        @Override
        public void flush() {
            em.flush();
        }

        @Override
        public void refresh(Address address) {
            em.refresh(address);
        }
    }
}
