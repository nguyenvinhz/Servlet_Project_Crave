package com.foodordering.validator;

import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.OptionType;
import com.foodordering.exception.BadRequestException;

import java.util.Collection;
import java.util.EnumSet;

/** Shared selection rules for cart writes and checkout revalidation. */
public final class OptionSelectionValidator {

    private OptionSelectionValidator() {
    }

    public static void validate(Collection<OptionType> optionTypes) {
        EnumSet<OptionType> selected = EnumSet.noneOf(OptionType.class);
        for (OptionType type : optionTypes) {
            if (type == OptionType.SIZE || type == OptionType.SUGAR_LEVEL || type == OptionType.ICE_LEVEL) {
                if (!selected.add(type)) {
                    throw new BadRequestException(ErrorCode.INVALID_OPTION,
                            "Chỉ được chọn một kích cỡ, một mức đường và một mức đá cho mỗi món");
                }
            }
        }
    }
}
