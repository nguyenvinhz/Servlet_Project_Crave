package com.foodordering.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO nhận dữ liệu yêu cầu thêm món vào giỏ hàng.
 */
public class AddToCartRequest {
    private String foodId;
    private int quantity = 1;
    private String note;
    private List<String> optionIds = new ArrayList<>();

    public AddToCartRequest() {
    }

    public AddToCartRequest(String foodId, int quantity, String note, List<String> optionIds) {
        this.foodId = foodId;
        this.quantity = quantity;
        this.note = note;
        this.optionIds = optionIds != null ? optionIds : new ArrayList<>();
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

    public List<String> getOptionIds() {
        return optionIds;
    }

    public void setOptionIds(List<String> optionIds) {
        this.optionIds = optionIds != null ? optionIds : new ArrayList<>();
    }
}
