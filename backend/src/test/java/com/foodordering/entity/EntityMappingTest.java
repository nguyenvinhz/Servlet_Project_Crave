package com.foodordering.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityMappingTest {

    @Test
    void entitiesMapToExistingSchemaTables() {
        assertEquals("user_account", User.class.getAnnotation(Table.class).name());
        assertEquals("customer", Customer.class.getAnnotation(Table.class).name());
        assertEquals("delivery_address", Address.class.getAnnotation(Table.class).name());
        assertEquals(InheritanceType.JOINED, User.class.getAnnotation(Inheritance.class).strategy());
        assertEquals("CUSTOMER", Customer.class.getAnnotation(DiscriminatorValue.class).value());
    }

    @Test
    void addingAddressKeepsBothSidesOfRelationshipConsistent() {
        Customer customer = new Customer("KH99", "Test User", "test@example.com", "0900000099", "hash");
        Address address = new Address("DC99", "1 Test Street", null, true);

        customer.addAddress(address);

        assertEquals(1, customer.getAddresses().size());
        assertSame(customer, address.getCustomer());
    }
}
