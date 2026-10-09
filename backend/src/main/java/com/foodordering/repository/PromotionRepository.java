package com.foodordering.repository;

import com.foodordering.entity.Promotion;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Day 1 persistence contract; database operations are implemented on Day 2. */
public class PromotionRepository {
    public Promotion findByCode(String code) {
        throw new UnsupportedOperationException("Promotion persistence is implemented on Day 2.");
    }

    public Promotion findById(String promotionId) {
        throw new UnsupportedOperationException("Promotion persistence is implemented on Day 2.");
    }

    public List<Promotion> findAllActive(LocalDateTime currentTime) {
        throw new UnsupportedOperationException("Promotion persistence is implemented on Day 2.");
    }

    public BigDecimal calculateDiscountViaDatabase(String promotionId, BigDecimal subtotal, LocalDateTime orderTime) {
        throw new UnsupportedOperationException("Promotion persistence is implemented on Day 2.");
    }
}
