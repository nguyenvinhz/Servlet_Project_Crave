package com.foodordering.bean;

import com.foodordering.enums.FoodStatus;

import java.math.BigDecimal;
import java.util.List;

public record FoodDetailBean(
        String id,
        String categoryId,
        String categoryName,
        String name,
        BigDecimal price,
        String imageUrl,
        String description,
        FoodStatus status,
        List<FoodOptionBean> options
) {
    public String getId() { return id; }
    public String getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getImageUrl() { return imageUrl; }
    public String getDescription() { return description; }
    public FoodStatus getStatus() { return status; }
    public List<FoodOptionBean> getOptions() { return options; }
}
