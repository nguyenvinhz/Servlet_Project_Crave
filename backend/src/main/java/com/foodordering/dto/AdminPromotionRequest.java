package com.foodordering.dto;

import com.foodordering.enums.DiscountType;
import com.foodordering.enums.PromotionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPromotionRequest(String code, String name, DiscountType discountType,
                                    BigDecimal discountValue, BigDecimal minimumOrderValue,
                                    BigDecimal maximumDiscount, LocalDateTime startAt,
                                    LocalDateTime endAt, PromotionStatus status) {
}
