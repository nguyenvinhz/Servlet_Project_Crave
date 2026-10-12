package com.foodordering.dto;

import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả xác thực các tùy chọn của món ăn phục vụ kiểm tra nghiệp vụ và tính toán giá.
 */
public record FoodOptionValidationResult(
        Food food,
        List<FoodOption> validatedOptions,
        BigDecimal basePrice,
        BigDecimal optionsExtraTotal,
        BigDecimal calculatedUnitPrice
) {
    public FoodOptionValidationResult {
        if (validatedOptions == null) {
            validatedOptions = List.of();
        }
    }
}
