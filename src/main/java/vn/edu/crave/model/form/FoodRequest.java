package vn.edu.crave.model.form;

import vn.edu.crave.model.enums.FoodStatus;

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
