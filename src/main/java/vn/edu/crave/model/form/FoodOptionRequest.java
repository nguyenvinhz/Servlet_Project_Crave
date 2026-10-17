package vn.edu.crave.model.form;

import vn.edu.crave.model.enums.OptionStatus;
import vn.edu.crave.model.enums.OptionType;

import java.math.BigDecimal;

public record FoodOptionRequest(
        String foodId,
        OptionType optionType,
        String name,
        BigDecimal extraPrice,
        OptionStatus status
) {
}
