package com.foodordering.bean;

import com.foodordering.enums.DiscountType;
import com.foodordering.enums.PromotionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * bean trả về thông tin chi tiết khuyến mãi cho client.
 */
public class PromotionBean {
    public PromotionBean(com.foodordering.model.Promotion entity) {

        this.setPromotionId(entity.getPromotionId());
        this.setCode(entity.getCode());
        this.setName(entity.getName());
        this.setDiscountType(entity.getDiscountType());
        this.setDiscountValue(entity.getDiscountValue());
        this.setMinimumOrderValue(entity.getMinimumOrderValue());
        this.setMaximumDiscount(entity.getMaximumDiscount());
        this.setStartAt(entity.getStartAt());
        this.setEndAt(entity.getEndAt());
        this.setStatus(entity.getStatus());

        if (entity.getDiscountType() == DiscountType.PERCENT) {
            String maxDesc = (entity.getMaximumDiscount() != null && entity.getMaximumDiscount().doubleValue() > 0)
                    ? String.format(", tối đa %,.0f đ", entity.getMaximumDiscount().doubleValue())
                    : "";
            this.setDescription(String.format("Giảm %s%% cho đơn từ %,.0f đ%s",
                    entity.getDiscountValue() != null ? entity.getDiscountValue().toPlainString() : "0",
                    entity.getMinimumOrderValue() != null ? entity.getMinimumOrderValue().doubleValue() : 0,
                    maxDesc));
        } else {
            this.setDescription(String.format("Giảm %,.0f đ cho đơn từ %,.0f đ",
                    entity.getDiscountValue() != null ? entity.getDiscountValue().doubleValue() : 0,
                    entity.getMinimumOrderValue() != null ? entity.getMinimumOrderValue().doubleValue() : 0));
        }

    }

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
    private String description;

    public PromotionBean() {
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
        this.minimumOrderValue = minimumOrderValue;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
