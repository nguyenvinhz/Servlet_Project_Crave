package com.foodordering.bean;

import com.foodordering.enums.FoodStatus;

import java.math.BigDecimal;

public record FoodSummaryBean(
        String id,
        String categoryId,
        String categoryName,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        FoodStatus status
) {
    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public FoodStatus getStatus() { return status; }
}
