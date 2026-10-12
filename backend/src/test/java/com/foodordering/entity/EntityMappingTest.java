package com.foodordering.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EntityMappingTest {

    @Test
    void diagramOrderAssociationsUseExistingForeignKeysWithoutDuplicateWrites() throws Exception {
        assertReadOnlyRelationship(CustomerOrder.class, "promotion", "promotion_id");
        assertReadOnlyRelationship(CustomerOrder.class, "assignedEmployee", "assigned_employee_id");
        assertReadOnlyRelationship(OrderDetail.class, "food", "food_id");
        assertReadOnlyRelationship(OrderStatusHistory.class, "changedByEmployee", "changed_by_employee_id");
    }

    private void assertReadOnlyRelationship(Class<?> entity, String fieldName, String columnName) throws Exception {
        var field = entity.getDeclaredField(fieldName);
        assertNotNull(field.getAnnotation(ManyToOne.class));
        JoinColumn column = field.getAnnotation(JoinColumn.class);
        assertNotNull(column);
        assertEquals(columnName, column.name());
        assertFalse(column.insertable());
        assertFalse(column.updatable());
    }

    @Test
    void entitiesMapToExistingSchemaTables() {
        assertEquals("user_account", User.class.getAnnotation(Table.class).name());
        assertEquals("customer", Customer.class.getAnnotation(Table.class).name());
        assertEquals("employee", Employee.class.getAnnotation(Table.class).name());
        assertEquals("delivery_address", Address.class.getAnnotation(Table.class).name());
        assertEquals("category", Category.class.getAnnotation(Table.class).name());
        assertEquals("food", Food.class.getAnnotation(Table.class).name());
        assertEquals("food_option", FoodOption.class.getAnnotation(Table.class).name());
        assertEquals(InheritanceType.JOINED, User.class.getAnnotation(Inheritance.class).strategy());
        assertEquals("CUSTOMER", Customer.class.getAnnotation(DiscriminatorValue.class).value());
        assertEquals("EMPLOYEE", Employee.class.getAnnotation(DiscriminatorValue.class).value());
    }

    @Test
    void addingAddressKeepsBothSidesOfRelationshipConsistent() {
        Customer customer = new Customer("KH99", "Test User", "test@example.com", "0900000099", "hash");
        Address address = new Address("DC99", "1 Test Street", null, true);

        customer.addAddress(address);

        assertEquals(1, customer.getAddresses().size());
        assertSame(customer, address.getCustomer());
    }

    @Test
    void addingFoodToCategoryKeepsBothSidesConsistent() {
        Category category = new Category("DM99", "Món thử", "Mô tả thử nghiệm");
        Food food = new Food("MA99", null, "Món ngon", java.math.BigDecimal.valueOf(50000), null, null);

        category.addFood(food);

        assertEquals(1, category.getFoods().size());
        assertSame(category, food.getCategory());
    }

    @Test
    void addingOptionToFoodKeepsBothSidesConsistent() {
        Category category = new Category("DM99", "Món thử", "Mô tả thử nghiệm");
        Food food = new Food("MA99", category, "Món ngon", java.math.BigDecimal.valueOf(50000), null, null);
        FoodOption option = new FoodOption("TC99", null, com.foodordering.enums.OptionType.SIZE, "Size L", java.math.BigDecimal.valueOf(5000));

        food.addOption(option);

        assertEquals(1, food.getOptions().size());
        assertSame(food, option.getFood());
    }
}
