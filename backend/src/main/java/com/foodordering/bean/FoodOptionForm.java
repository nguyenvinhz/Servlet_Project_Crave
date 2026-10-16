package com.foodordering.bean;

import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;

import java.math.BigDecimal;

public record FoodOptionForm(
        String foodId,
        OptionType optionType,
        String name,
        BigDecimal extraPrice,
        OptionStatus status
) {
}
