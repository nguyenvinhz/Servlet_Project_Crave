package com.foodordering.validator;

import com.foodordering.entity.Promotion;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Day 1 validation contract; voucher rules are implemented on Day 2. */
public final class PromotionValidator {
    private PromotionValidator() {
    }

    public static void validatePromotionCode(String code) {
        throw new UnsupportedOperationException("Promotion validation is implemented on Day 2.");
    }

    public static void validateApplicable(Promotion promotion, BigDecimal subtotal, LocalDateTime orderTime) {
        throw new UnsupportedOperationException("Promotion validation is implemented on Day 2.");
    }
}
