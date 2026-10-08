package com.foodordering.entity;

import com.foodordering.enums.CustomerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer")
@PrimaryKeyJoinColumn(name = "customer_id")
@DiscriminatorValue("CUSTOMER")
public class Customer extends User {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "registered_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime registeredAt;

    public Customer() {
    }

    public Customer(String id) {
        super(id, null, null, null, null);
    }

    public Customer(String id, String fullName, String email, String phone, String passwordHash) {
        super(id, fullName, email, phone, passwordHash);
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerStatus status) {
        this.status = status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
}
