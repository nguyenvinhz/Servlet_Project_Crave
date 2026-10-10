package com.foodordering.dto;

import com.foodordering.enums.FoodStatus;

import java.math.BigDecimal;

public record FoodRequest(
        String categoryId,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        FoodStatus status
) {
}
