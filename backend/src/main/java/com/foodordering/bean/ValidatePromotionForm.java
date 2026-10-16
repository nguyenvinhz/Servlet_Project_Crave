package com.foodordering.bean;

import java.math.BigDecimal;

public class ValidatePromotionForm {
    private String code;
    private BigDecimal subtotal;

    public ValidatePromotionForm() {
    }

    public ValidatePromotionForm(String code, BigDecimal subtotal) {
        this.code = code;
        this.subtotal = subtotal;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
