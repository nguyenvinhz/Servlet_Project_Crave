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
    // Tomcat 10.1 JSP EL resolves JavaBean getters rather than record accessors.
    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public FoodStatus getStatus() { return status; }
}
