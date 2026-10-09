package com.foodordering.service;

import com.foodordering.dto.PromotionDto;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.entity.Promotion;
import com.foodordering.mapper.PromotionMapper;
import com.foodordering.repository.PromotionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Day 1 business contract; voucher validation and discounts are implemented on Day 2. */
public class PromotionService {
    public PromotionService() {
    }

    public PromotionService(PromotionRepository promotionRepository, CartService cartService) {
    }

    public PromotionService(PromotionRepository promotionRepository, CartService cartService,
                            PromotionMapper promotionMapper) {
    }

    public PromotionValidationResultDto validatePromotion(ValidatePromotionRequest request, String customerId) {
        throw new UnsupportedOperationException("Promotion business logic is implemented on Day 2.");
    }

    public PromotionValidationResultDto validatePromotion(String code, String customerId,
                                                         BigDecimal requestedSubtotal, LocalDateTime orderTime) {
        throw new UnsupportedOperationException("Promotion business logic is implemented on Day 2.");
    }

    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal, LocalDateTime orderTime) {
        throw new UnsupportedOperationException("Promotion business logic is implemented on Day 2.");
    }

    public List<PromotionDto> getActivePromotions() {
        throw new UnsupportedOperationException("Promotion business logic is implemented on Day 2.");
    }

    public PromotionDto getPromotionByCode(String code) {
        throw new UnsupportedOperationException("Promotion business logic is implemented on Day 2.");
    }

    public PromotionDto mapToDto(Promotion promotion) {
        throw new UnsupportedOperationException("Promotion service mapping is implemented on Day 2.");
    }
}
