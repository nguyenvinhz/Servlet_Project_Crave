package com.foodordering.bean;

import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;

import java.math.BigDecimal;

public record FoodOptionBean(
        String id,
        String foodId,
        OptionType optionType,
        String name,
        BigDecimal extraPrice,
        OptionStatus status
) {
    public String getId() { return id; }
    public String getFoodId() { return foodId; }
    public OptionType getOptionType() { return optionType; }
    public String getName() { return name; }
    public BigDecimal getExtraPrice() { return extraPrice; }
    public OptionStatus getStatus() { return status; }
}
