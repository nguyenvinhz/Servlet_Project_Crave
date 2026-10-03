package com.foodordering.entity;

import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng cart trong cơ sở dữ liệu.
 */
public class Cart {
    private String cartId;
    private String customerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Cart() {
    }

    public Cart(String cartId, String customerId) {
        this.cartId = cartId;
        this.customerId = customerId;
    }

    public Cart(String cartId, String customerId, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.cartId = cartId;
        this.customerId = customerId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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
