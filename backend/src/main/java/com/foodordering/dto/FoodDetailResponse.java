package com.foodordering.dto;

import com.foodordering.enums.FoodStatus;

import java.math.BigDecimal;
import java.util.List;

public record FoodDetailResponse(
        String id,
        String categoryId,
        String categoryName,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        FoodStatus status,
        List<FoodOptionResponse> options
) {
}
