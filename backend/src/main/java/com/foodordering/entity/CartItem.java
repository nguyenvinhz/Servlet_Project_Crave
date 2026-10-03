package com.foodordering.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity đại diện cho bảng cart_item trong cơ sở dữ liệu.
 */
public class CartItem {
    private String cartItemId;
    private String cartId;
    private String foodId;
    private int quantity;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Danh sách các ID tùy chọn đính kèm món này
    private List<String> optionIds = new ArrayList<>();

    public CartItem() {
    }

    public CartItem(String cartItemId, String cartId, String foodId, int quantity, String note) {
        this.cartItemId = cartItemId;
        this.cartId = cartId;
        this.foodId = foodId;
        this.quantity = quantity;
        this.note = note;
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public void setCartItemId(String cartItemId) {
        this.cartItemId = cartItemId;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getFoodId() {
        return foodId;
    }

    public void setFoodId(String foodId) {
        this.foodId = foodId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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

    public List<String> getOptionIds() {
        return optionIds;
    }

    public void setOptionIds(List<String> optionIds) {
        this.optionIds = optionIds != null ? optionIds : new ArrayList<>();
    }
}
