package com.foodordering.enums;

/**
 * Trạng thái của khuyến mãi.
 * Khớp với ENUM('ACTIVE','INACTIVE') trong bảng promotion.
 */
public enum PromotionStatus {
    ACTIVE,
    INACTIVE;

    public static PromotionStatus fromString(String value) {
        if (value == null) return null;
        try {
            return PromotionStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
