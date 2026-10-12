package com.foodordering.enums;

/**
 * Loại giảm giá cho khuyến mãi.
 * Khớp với ENUM('PERCENT','FIXED_AMOUNT') trong bảng promotion.
 */
public enum DiscountType {
    PERCENT,
    FIXED_AMOUNT;

    public static DiscountType fromString(String value) {
        if (value == null) return null;
        try {
            return DiscountType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
