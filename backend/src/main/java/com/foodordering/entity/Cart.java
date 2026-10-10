package com.foodordering.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity JPA đại diện cho bảng cart trong cơ sở dữ liệu Crave.
 */
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @Column(name = "cart_id", length = 10, nullable = false)
    private String cartId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> items = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Transient
    private String customerId;

    public Cart() {
    }

    public Cart(String cartId, Customer customer) {
        this.cartId = cartId;
        this.customer = customer;
        if (customer != null) {
            this.customerId = customer.getId();
        }
    }

    public Cart(String cartId, String customerId) {
        this.cartId = cartId;
        this.customerId = customerId;
        if (customerId != null) {
            this.customer = new Customer(customerId);
        }
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null) {
            this.customerId = customer.getId();
        }
    }

    public String getCustomerId() {
        if (customer != null) {
            return customer.getId();
        }
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
        if (customerId != null) {
            this.customer = new Customer(customerId);
        }
    }

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
