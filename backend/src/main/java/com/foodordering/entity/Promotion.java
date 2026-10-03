package com.foodordering.entity;

import com.foodordering.enums.DiscountType;
import com.foodordering.enums.PromotionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity đại diện cho bảng promotion trong cơ sở dữ liệu.
 */
public class Promotion {
    private String promotionId;
    private String code;
    private String name;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrderValue;
    private BigDecimal maximumDiscount;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private PromotionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Promotion() {
        this.minimumOrderValue = BigDecimal.ZERO;
        this.status = PromotionStatus.ACTIVE;
    }

    public String getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(String promotionId) {
        this.promotionId = promotionId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMinimumOrderValue() {
        return minimumOrderValue;
    }

    public void setMinimumOrderValue(BigDecimal minimumOrderValue) {
        this.minimumOrderValue = minimumOrderValue != null ? minimumOrderValue : BigDecimal.ZERO;
    }

    public BigDecimal getMaximumDiscount() {
        return maximumDiscount;
    }

    public void setMaximumDiscount(BigDecimal maximumDiscount) {
        this.maximumDiscount = maximumDiscount;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public PromotionStatus getStatus() {
        return status;
    }

    public void setStatus(PromotionStatus status) {
        this.status = status;
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

    /**
     * Kiểm tra khuyến mãi có còn hiệu lực tại một thời điểm nhất định hay không.
     */
    public boolean isCurrentlyActive(LocalDateTime currentTime) {
        if (status != PromotionStatus.ACTIVE) {
            return false;
        }
        if (currentTime == null) {
            currentTime = LocalDateTime.now();
        }
        return !currentTime.isBefore(startAt) && !currentTime.isAfter(endAt);
    }
}
