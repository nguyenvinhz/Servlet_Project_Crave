package com.foodordering.bean;

import java.util.List;

public class UpdateCartItemForm {
    private String cartItemId;
    private int quantity;
    private String note;
    // null preserves current options; an empty list removes all options.
    private List<String> optionIds;

    public UpdateCartItemForm() {
    }

    public UpdateCartItemForm(String cartItemId, int quantity, String note) {
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

    public List<String> getOptionIds() {
        return optionIds;
    }

    public void setOptionIds(List<String> optionIds) {
        this.optionIds = optionIds;
    }
}
