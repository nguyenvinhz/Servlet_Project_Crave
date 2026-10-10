package com.foodordering.dto;

import java.math.BigDecimal;

/**
 * DTO chứa thông tin snapshot tùy chọn của món ăn trong giỏ hàng.
 */
public class CartItemOptionDto {
    private String optionId;
    private String name;
    private String optionType;
    private BigDecimal extraPrice;

    public CartItemOptionDto() {
        this.extraPrice = BigDecimal.ZERO;
    }

    public CartItemOptionDto(String optionId, String name, String optionType, BigDecimal extraPrice) {
        this.optionId = optionId;
        this.name = name;
        this.optionType = optionType;
        this.extraPrice = extraPrice != null ? extraPrice : BigDecimal.ZERO;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOptionType() {
        return optionType;
    }

    public void setOptionType(String optionType) {
        this.optionType = optionType;
    }

    public BigDecimal getExtraPrice() {
        return extraPrice;
    }

    public void setExtraPrice(BigDecimal extraPrice) {
        this.extraPrice = extraPrice;
    }
}
