package vn.edu.crave.model.form;

import vn.edu.crave.model.enums.DiscountType;
import vn.edu.crave.model.enums.PromotionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPromotionRequest(String code, String name, DiscountType discountType,
                                    BigDecimal discountValue, BigDecimal minimumOrderValue,
                                    BigDecimal maximumDiscount, LocalDateTime startAt,
                                    LocalDateTime endAt, PromotionStatus status) {
}
