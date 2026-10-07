package com.foodordering.dto;

public record CreateCategoryRequest(
        String name,
        String description
) {
}
