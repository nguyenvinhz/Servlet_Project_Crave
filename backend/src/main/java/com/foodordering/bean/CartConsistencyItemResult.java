package com.foodordering.bean;

import com.foodordering.enums.FoodStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả kiểm tra tính nhất quán của một món trong giỏ hàng.
 */
public record CartConsistencyItemResult(
        String foodId,
        String foodName,
        boolean consistent,
        boolean available,
        FoodStatus foodStatus,
        boolean optionsValid,
        List<String> invalidOptionNames,
        boolean priceConsistent,
        BigDecimal currentUnitPrice,
        BigDecimal expectedUnitPrice,
        String issueMessage
) {
    public CartConsistencyItemResult {
        if (invalidOptionNames == null) {
            invalidOptionNames = List.of();
        }
    }
}
