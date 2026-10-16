package com.foodordering.dao;

import com.foodordering.model.Address;
import com.foodordering.model.Customer;
import com.foodordering.model.EmailVerificationChallenge;
import com.foodordering.model.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Keeps each account operation in one persistence context and transaction. */
public interface AccountDAO {

    <T> T read(Function<Session, T> operation);

    <T> T write(Function<Session, T> operation);

    interface Session {
        Optional<User> findUser(String id);

        Optional<User> findByEmail(String email);

        default Optional<User> findUser(String id, boolean lock) {
            return findUser(id);
        }

        default Optional<User> findByEmail(String email, boolean lock) {
            return findByEmail(email);
        }

        default Optional<EmailVerificationChallenge> findVerificationChallenge(String id, boolean lock) {
            throw new UnsupportedOperationException("Email verification persistence is unavailable.");
        }

        default void persistVerificationChallenge(EmailVerificationChallenge challenge) {
            throw new UnsupportedOperationException("Email verification persistence is unavailable.");
        }

        Optional<Customer> findCustomer(String id, boolean lock);

        Map<String, String> findContactConflicts(String email, String phone, String excludedId);

        boolean userIdExists(String id);

        boolean addressIdExists(String id);

        List<Address> findAddresses(String customerId);

        Optional<Address> findAddress(String customerId, String addressId);

        void persistCustomer(Customer customer);

        void persistAddress(Address address);

        void removeAddress(Address address);

        void flush();

        void refresh(Address address);
    }
}
