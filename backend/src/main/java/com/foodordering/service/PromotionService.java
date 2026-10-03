package com.foodordering.service;

import com.foodordering.dto.PromotionDto;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.repository.PromotionRepository;
import com.foodordering.validator.PromotionValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service xử lý nghiệp vụ khuyến mãi và kiểm tra voucher (Promotion slice do Ung Văn Trí phụ trách).
 */
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final CartService cartService;

    public PromotionService() {
        this.promotionRepository = new PromotionRepository();
        this.cartService = new CartService();
    }

    public PromotionService(PromotionRepository promotionRepository, CartService cartService) {
        this.promotionRepository = promotionRepository;
        this.cartService = cartService;
    }

    /**
     * Xác thực voucher và tính toán số tiền giảm giá một cách an toàn trên server:
     * - Nếu client không truyền subtotal hoặc truyền giá trị <= 0, lấy subtotal thật từ giỏ hàng hiện tại của customerId.
     * - Kiểm tra hạn sử dụng, trạng thái kích hoạt, giá trị đơn tối thiểu.
     * - Tính toán chính xác theo loại % (có trần maximum_discount) hoặc số tiền cố định.
     */
    public PromotionValidationResultDto validatePromotion(String code, String customerId, BigDecimal requestedSubtotal, LocalDateTime orderTime) {
        PromotionValidator.validatePromotionCode(code);

        // 1. Tìm thông tin mã khuyến mãi trong DB
        Promotion promotion = promotionRepository.findByCode(code.trim().toUpperCase());
        if (promotion == null) {
            return PromotionValidationResultDto.invalid("Mã khuyến mãi không tồn tại", ErrorCode.PROMOTION_NOT_FOUND.getCode());
        }

        // 2. Xác định subtotal đáng tin cậy
        BigDecimal subtotal = requestedSubtotal;
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            if (customerId != null && !customerId.trim().isEmpty()) {
                subtotal = cartService.getVerifiedSubtotal(customerId);
            } else {
                subtotal = BigDecimal.ZERO;
            }
        }

        // 3. Kiểm tra điều kiện áp dụng
        try {
            PromotionValidator.validateApplicable(promotion, subtotal, orderTime);
        } catch (PromotionValidationException e) {
            return PromotionValidationResultDto.invalid(e.getMessage(), e.getErrorCode().getCode());
        }

        // 4. Tính toán số tiền được giảm
        BigDecimal discountAmount = calculateDiscount(promotion, subtotal, orderTime);

        PromotionDto dto = mapToDto(promotion);
        return PromotionValidationResultDto.valid(dto, subtotal, discountAmount);
    }

    /**
     * Thuật toán tính toán số tiền giảm giá chuẩn xác theo quy tắc trong schema database.
     */
    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal, LocalDateTime orderTime) {
        if (promotion == null || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Thử tính qua procedure của MySQL trước để đảm bảo tính đồng nhất 100% với DB trigger
        BigDecimal procDiscount = promotionRepository.calculateDiscountViaDatabase(promotion.getPromotionId(), subtotal, orderTime);
        if (procDiscount != null) {
            return procDiscount;
        }

        // Tính toán dự phòng trên Java (khớp logic stored procedure sp_calculate_discount)
        BigDecimal discount = BigDecimal.ZERO;
        if (promotion.getDiscountType() == DiscountType.PERCENT) {
            discount = subtotal.multiply(promotion.getDiscountValue())
                               .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        } else if (promotion.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            discount = promotion.getDiscountValue();
        }

        if (promotion.getMaximumDiscount() != null && promotion.getMaximumDiscount().compareTo(BigDecimal.ZERO) > 0) {
            discount = discount.min(promotion.getMaximumDiscount());
        }

        // Số tiền giảm không bao giờ được vượt quá giá trị đơn hàng
        discount = discount.min(subtotal);
        return discount.max(BigDecimal.ZERO);
    }

    /**
     * Lấy danh sách toàn bộ các mã khuyến mãi đang có hiệu lực để hiển thị cho khách hàng chọn.
     */
    public List<PromotionDto> getActivePromotions() {
        List<Promotion> promotions = promotionRepository.findAllActive(LocalDateTime.now());
        List<PromotionDto> result = new ArrayList<>();
        for (Promotion p : promotions) {
            result.add(mapToDto(p));
        }
        return result;
    }

    /**
     * Lấy thông tin chi tiết của một mã khuyến mãi theo mã code.
     */
    public PromotionDto getPromotionByCode(String code) {
        Promotion p = promotionRepository.findByCode(code);
        return p != null ? mapToDto(p) : null;
    }

    public PromotionDto mapToDto(Promotion p) {
        if (p == null) return null;
        PromotionDto dto = new PromotionDto();
        dto.setPromotionId(p.getPromotionId());
        dto.setCode(p.getCode());
        dto.setName(p.getName());
        dto.setDiscountType(p.getDiscountType());
        dto.setDiscountValue(p.getDiscountValue());
        dto.setMinimumOrderValue(p.getMinimumOrderValue());
        dto.setMaximumDiscount(p.getMaximumDiscount());
        dto.setStartAt(p.getStartAt());
        dto.setEndAt(p.getEndAt());
        dto.setStatus(p.getStatus());

        if (p.getDiscountType() == DiscountType.PERCENT) {
            String maxDesc = p.getMaximumDiscount() != null ? String.format(", tối đa %,.0f đ", p.getMaximumDiscount().doubleValue()) : "";
            dto.setDescription(String.format("Giảm %s%% cho đơn từ %,.0f đ%s", p.getDiscountValue().toPlainString(), p.getMinimumOrderValue().doubleValue(), maxDesc));
        } else {
            dto.setDescription(String.format("Giảm %,.0f đ cho đơn từ %,.0f đ", p.getDiscountValue().doubleValue(), p.getMinimumOrderValue().doubleValue()));
        }

        return dto;
    }
}
