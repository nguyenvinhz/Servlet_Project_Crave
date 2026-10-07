package com.foodordering.dto;

public record UpdateCategoryRequest(
        String name,
        String description
) {
}
