package vn.edu.crave.model.validator;

import vn.edu.crave.model.enums.OptionType;
import vn.edu.crave.exception.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OptionSelectionValidatorTest {

    @ParameterizedTest
    @EnumSource(value = OptionType.class, names = {"SIZE", "SUGAR_LEVEL", "ICE_LEVEL"})
    void singleChoiceGroupsRejectMultipleSelections(OptionType type) {
        assertThrows(BadRequestException.class,
                () -> OptionSelectionValidator.validate(List.of(type, type)));
    }

    @Test
    void differentGroupsAndSeveralToppingsCanBeCombined() {
        assertDoesNotThrow(() -> OptionSelectionValidator.validate(List.of(
                OptionType.SIZE, OptionType.SUGAR_LEVEL, OptionType.ICE_LEVEL,
                OptionType.TOPPING, OptionType.TOPPING, OptionType.OTHER)));
    }
}
