package com.foodordering.dto;

public class UpdateCartItemRequest {
    private String cartItemId;
    private int quantity;
    private String note;

    public UpdateCartItemRequest() {
    }

    public UpdateCartItemRequest(String cartItemId, int quantity, String note) {
        this.cartItemId = cartItemId;
        this.quantity = quantity;
        this.note = note;
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public void setCartItemId(String cartItemId) {
        this.cartItemId = cartItemId;
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
}
