package com.foodordering.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho một dòng món ăn trong giỏ hàng.
 * Đơn giá và thành tiền được tính toán hoàn toàn phía server.
 */
public class CartItemDto {
    private String cartItemId;
    private String cartId;
    private String foodId;
    private String foodName;
    private String imageUrl;
    private BigDecimal basePrice;
    private int quantity;
    private String note;
    private List<CartItemOptionDto> options = new ArrayList<>();
    private BigDecimal optionTotal = BigDecimal.ZERO;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public CartItemDto() {
    }

    /**
     * Tự động tính optionTotal, unitPrice và lineTotal phía server.
     */
    public void calculateTotals() {
        BigDecimal sumOption = BigDecimal.ZERO;
        if (options != null) {
            for (CartItemOptionDto opt : options) {
                if (opt.getExtraPrice() != null) {
                    sumOption = sumOption.add(opt.getExtraPrice());
                }
            }
        }
        this.optionTotal = sumOption;
        BigDecimal base = this.basePrice != null ? this.basePrice : BigDecimal.ZERO;
        this.unitPrice = base.add(this.optionTotal);
        this.lineTotal = this.unitPrice.multiply(BigDecimal.valueOf(Math.max(0, this.quantity)));
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

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
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

    public List<CartItemOptionDto> getOptions() {
        return options;
    }

    public void setOptions(List<CartItemOptionDto> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    public BigDecimal getOptionTotal() {
        return optionTotal;
    }

    public void setOptionTotal(BigDecimal optionTotal) {
        this.optionTotal = optionTotal;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
