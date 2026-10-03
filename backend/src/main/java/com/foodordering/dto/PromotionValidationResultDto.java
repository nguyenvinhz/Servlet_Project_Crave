package com.foodordering.dto;

import java.math.BigDecimal;

/**
 * DTO trả về kết quả kiểm tra và tính toán giảm giá của voucher.
 */
public class PromotionValidationResultDto {
    private boolean valid;
    private String message;
    private PromotionDto promotion;
    private BigDecimal originalSubtotal;
    private BigDecimal discountAmount;
    private BigDecimal finalSubtotal;
    private String errorCode;

    public PromotionValidationResultDto() {
        this.originalSubtotal = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.finalSubtotal = BigDecimal.ZERO;
    }

    public static PromotionValidationResultDto valid(PromotionDto promotion, BigDecimal originalSubtotal, BigDecimal discountAmount) {
        PromotionValidationResultDto res = new PromotionValidationResultDto();
        res.setValid(true);
        res.setMessage("Áp dụng mã khuyến mãi thành công");
        res.setPromotion(promotion);
        res.setOriginalSubtotal(originalSubtotal);
        res.setDiscountAmount(discountAmount);
        res.setFinalSubtotal(originalSubtotal.subtract(discountAmount).max(BigDecimal.ZERO));
        return res;
    }

    public static PromotionValidationResultDto invalid(String message, String errorCode) {
        PromotionValidationResultDto res = new PromotionValidationResultDto();
        res.setValid(false);
        res.setMessage(message);
        res.setErrorCode(errorCode);
        return res;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public PromotionDto getPromotion() {
        return promotion;
    }

    public void setPromotion(PromotionDto promotion) {
        this.promotion = promotion;
    }

    public BigDecimal getOriginalSubtotal() {
        return originalSubtotal;
    }

    public void setOriginalSubtotal(BigDecimal originalSubtotal) {
        this.originalSubtotal = originalSubtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getFinalSubtotal() {
        return finalSubtotal;
    }

    public void setFinalSubtotal(BigDecimal finalSubtotal) {
        this.finalSubtotal = finalSubtotal;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }
}
