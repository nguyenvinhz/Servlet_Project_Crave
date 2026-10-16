package com.foodordering.bean;

import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;

import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JspBeanCompatibilityTest {
    @Test
    void menuRecordsExposePropertiesUsedByServlet6Jsp() throws Exception {
        var category = new CategoryBean("DM01", "Món chính", "Cơm và mì", 1);
        var option = new FoodOptionBean("TC01", "MA01", OptionType.SIZE, "Lớn", BigDecimal.TEN, OptionStatus.ACTIVE);
        var food = new FoodDetailBean("MA01", "DM01", category.name(), "Cơm", BigDecimal.valueOf(45000),
                "", "Cơm nóng", FoodStatus.AVAILABLE, List.of(option));
        var summary = new FoodSummaryBean(food.id(), food.categoryId(), food.categoryName(), food.name(),
                food.price(), food.imageUrl(), food.description(), food.status());

        assertEquals(category.id(), property(category, "id"));
        assertEquals(category.name(), property(category, "name"));
        assertEquals(category.foodCount(), property(category, "foodCount"));
        assertEquals(summary.price(), property(summary, "price"));
        assertEquals(summary.status(), property(summary, "status"));
        assertEquals(food.options(), property(food, "options"));
        assertEquals(option.optionType(), property(option, "optionType"));
        assertEquals(option.extraPrice(), property(option, "extraPrice"));


    }

    @Test
    void adminEmployeeExposesReadOnlyJspProperties() throws Exception {
        LocalDate hired = LocalDate.of(2026, 10, 1);
        var employee = new AdminEmployeeBean("NV02", "Nhân viên", "staff@example.com", "0901234567",
                EmployeeRole.ORDER_STAFF, EmployeeStatus.WORKING, "Địa chỉ nhân viên", hired);

        readOnlyProperties(employee, Map.of("id", "NV02", "fullName", "Nhân viên", "email", "staff@example.com",
                "phone", "0901234567", "role", EmployeeRole.ORDER_STAFF, "status", EmployeeStatus.WORKING,
                "address", "Địa chỉ nhân viên", "hireDate", hired));
    }

    @Test
    void adminCustomerExposesReadOnlyJspProperties() throws Exception {
        LocalDateTime registered = LocalDateTime.of(2026, 10, 1, 9, 30);
        var customer = new AdminCustomerBean("KH02", "Khách hàng", "customer@example.com", "0901234568",
                CustomerStatus.ACTIVE, registered, 3, new BigDecimal("150000"));

        readOnlyProperties(customer, Map.of("id", "KH02", "fullName", "Khách hàng", "email", "customer@example.com",
                "phone", "0901234568", "status", CustomerStatus.ACTIVE, "registeredAt", registered,
                "orderCount", 3L, "totalSpent", new BigDecimal("150000")));
    }

    private void readOnlyProperties(Object bean, Map<String, Object> expected) throws Exception {
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), property(bean, entry.getKey()), "JSP property " + entry.getKey());
        }
        for (var descriptor : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
            if (expected.containsKey(descriptor.getName())) {
                assertNull(descriptor.getWriteMethod(), "View property must remain read-only: " + descriptor.getName());
            }
        }
    }

    private Object property(Object bean, String name) throws Exception {
        for (var descriptor : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {
            if (descriptor.getName().equals(name) && descriptor.getReadMethod() != null) {
                return descriptor.getReadMethod().invoke(bean);
            }
        }
        throw new AssertionError("Missing JSP property " + name + " on " + bean.getClass().getSimpleName());
    }
}
