package com.foodordering.dto;

import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;

import java.math.BigDecimal;

public record FoodOptionRequest(
        String foodId,
        OptionType optionType,
        String name,
        BigDecimal extraPrice,
        OptionStatus status
) {
}
