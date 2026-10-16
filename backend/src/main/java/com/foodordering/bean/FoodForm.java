package com.foodordering.bean;

import com.foodordering.enums.FoodStatus;

import java.math.BigDecimal;

public record FoodForm(
        String categoryId,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        FoodStatus status
) {
}
