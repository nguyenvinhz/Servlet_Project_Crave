package com.foodordering.dto;

import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;

import java.math.BigDecimal;

public record FoodOptionResponse(
        String id,
        String foodId,
        OptionType optionType,
        String name,
        BigDecimal extraPrice,
        OptionStatus status
) {
    // Tomcat 10.1 JSP EL resolves JavaBean getters rather than record accessors.
    public String getId() { return id; }
    public String getFoodId() { return foodId; }
    public OptionType getOptionType() { return optionType; }
    public String getName() { return name; }
    public BigDecimal getExtraPrice() { return extraPrice; }
    public OptionStatus getStatus() { return status; }
}
