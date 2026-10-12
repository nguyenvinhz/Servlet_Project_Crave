package com.foodordering.dto;

import java.math.BigDecimal;

public class PromotionValidationResultDto {
    private boolean valid;
    private String message;
    private PromotionDto promotion;
    private BigDecimal originalSubtotal = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal finalSubtotal = BigDecimal.ZERO;
    private String errorCode;

    public PromotionValidationResultDto() {
    }

    public PromotionValidationResultDto(boolean valid, String message, String errorCode) {
        this.valid = valid;
        this.message = message;
        this.errorCode = errorCode;
    }

    public PromotionValidationResultDto(boolean valid, String message, PromotionDto promotion, BigDecimal originalSubtotal, BigDecimal discountAmount, BigDecimal finalSubtotal) {
        this.valid = valid;
        this.message = message;
        this.promotion = promotion;
        this.originalSubtotal = originalSubtotal != null ? originalSubtotal : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.finalSubtotal = finalSubtotal != null ? finalSubtotal : BigDecimal.ZERO;
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
