package com.foodordering.dto;

public record CategoryResponse(
        String id,
        String name,
        String description,
        long foodCount
) {
}
