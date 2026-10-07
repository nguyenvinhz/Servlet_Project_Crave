package com.foodordering.dto;

import com.foodordering.enums.FoodStatus;

import java.math.BigDecimal;

public record FoodSummaryResponse(
        String id,
        String categoryId,
        String categoryName,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        FoodStatus status
) {
}
