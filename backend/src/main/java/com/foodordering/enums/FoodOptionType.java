package com.foodordering.enums;

/**
 * Phân loại tùy chọn món ăn.
 * Khớp với ENUM('SIZE','TOPPING','SUGAR_LEVEL','ICE_LEVEL','OTHER') trong bảng food_option.
 */
public enum FoodOptionType {
    SIZE,
    TOPPING,
    SUGAR_LEVEL,
    ICE_LEVEL,
    OTHER;

    public static FoodOptionType fromString(String value) {
        if (value == null) return null;
        try {
            return FoodOptionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
