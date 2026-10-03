package com.foodordering.dto;

import java.math.BigDecimal;

/**
 * DTO nhận yêu cầu kiểm tra mã voucher.
 * Subtotal là tùy chọn: Nếu client không gửi hoặc gửi sai, server sẽ tự động lấy
 * subtotal thực tế từ giỏ hàng hiện tại của customer.
 */
public class ValidatePromotionRequest {
    private String code;
    private BigDecimal subtotal;

    public ValidatePromotionRequest() {
    }

    public ValidatePromotionRequest(String code, BigDecimal subtotal) {
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
