package com.foodordering.bean;

import com.foodordering.enums.CustomerStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCustomerBean(String id, String fullName, String email, String phone,
                                    CustomerStatus status, LocalDateTime registeredAt,
                                    long orderCount, BigDecimal totalSpent) {
    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public CustomerStatus getStatus() { return status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public long getOrderCount() { return orderCount; }
    public BigDecimal getTotalSpent() { return totalSpent; }
}
