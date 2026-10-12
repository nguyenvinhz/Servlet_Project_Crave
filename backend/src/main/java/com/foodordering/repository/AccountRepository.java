package com.foodordering.repository;

import com.foodordering.entity.Address;
import com.foodordering.entity.Customer;
import com.foodordering.entity.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Keeps each account operation in one persistence context and transaction. */
public interface AccountRepository {

    <T> T read(Function<Session, T> operation);

    <T> T write(Function<Session, T> operation);

    interface Session {
        Optional<User> findUser(String id);

        Optional<User> findByEmail(String email);

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
