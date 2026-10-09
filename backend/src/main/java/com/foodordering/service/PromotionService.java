package com.foodordering.service;

import com.foodordering.dto.PromotionDto;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.mapper.PromotionMapper;
import com.foodordering.repository.PromotionRepository;
import com.foodordering.validator.PromotionValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final CartService cartService;
    private final PromotionMapper promotionMapper;

    public PromotionService() {
        this.promotionRepository = new PromotionRepository();
        this.cartService = new CartService();
        this.promotionMapper = new PromotionMapper();
    }

    public PromotionService(PromotionRepository promotionRepository, CartService cartService) {
        this.promotionRepository = promotionRepository;
        this.cartService = cartService;
        this.promotionMapper = new PromotionMapper();
    }

    public PromotionService(PromotionRepository promotionRepository, CartService cartService, PromotionMapper promotionMapper) {
        this.promotionRepository = promotionRepository;
        this.cartService = cartService;
        this.promotionMapper = promotionMapper != null ? promotionMapper : new PromotionMapper();
    }

    /**
     * Nhận trực tiếp DTO ValidatePromotionRequest từ Servlet/Controller để xử lý xác thực voucher.
     */
    public PromotionValidationResultDto validatePromotion(ValidatePromotionRequest request, String customerId) {
        if (request == null) {
            return new PromotionValidationResultDto(false, "Dữ liệu yêu cầu không được để trống", ErrorCode.BAD_REQUEST.getCode());
        }
        return validatePromotion(request.getCode(), customerId, request.getSubtotal(), LocalDateTime.now());
    }

    /**
     * Xác thực voucher và tính toán số tiền giảm giá một cách an toàn trên server:
     * - Nếu client không truyền subtotal hoặc truyền giá trị <= 0, lấy subtotal thật từ giỏ hàng hiện tại của customerId.
     * - Kiểm tra hạn sử dụng theo orderTime, trạng thái kích hoạt, giá trị đơn tối thiểu.
     * - Tính toán chính xác theo loại % (có trần maximum_discount) hoặc số tiền cố định.
     */
    public PromotionValidationResultDto validatePromotion(String code, String customerId, BigDecimal requestedSubtotal, LocalDateTime orderTime) {
        try {
            PromotionValidator.validatePromotionCode(code);
        } catch (Exception e) {
            return new PromotionValidationResultDto(false, e.getMessage(), ErrorCode.BAD_REQUEST.getCode());
        }

        // 1. Tìm thông tin mã khuyến mãi trong DB (Repository trả về Entity)
        Promotion promotion = promotionRepository.findByCode(code.trim().toUpperCase());
        if (promotion == null) {
            return new PromotionValidationResultDto(false, "Mã khuyến mãi không tồn tại", ErrorCode.PROMOTION_NOT_FOUND.getCode());
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

        LocalDateTime effectiveOrderTime = orderTime != null ? orderTime : LocalDateTime.now();

        // 3. Kiểm tra điều kiện áp dụng với orderTime
        try {
            PromotionValidator.validateApplicable(promotion, subtotal, effectiveOrderTime);
        } catch (PromotionValidationException e) {
            return new PromotionValidationResultDto(false, e.getMessage(), e.getErrorCode().getCode());
        }

        // 4. Tính toán số tiền được giảm
        BigDecimal discountAmount = calculateDiscount(promotion, subtotal, effectiveOrderTime);
        BigDecimal finalSubtotal = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        // Chuyển đổi Entity sang DTO qua mapper thủ công
        PromotionDto dto = promotionMapper.toDto(promotion);
        return new PromotionValidationResultDto(true, "Áp dụng mã khuyến mãi thành công", dto, subtotal, discountAmount, finalSubtotal);
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
        return promotionMapper.toDtoList(promotions);
    }

    /**
     * Lấy thông tin chi tiết của một mã khuyến mãi theo mã code.
     */
    public PromotionDto getPromotionByCode(String code) {
        Promotion p = promotionRepository.findByCode(code);
        return p != null ? promotionMapper.toDto(p) : null;
    }

    /**
     * Phương thức tiện ích chuyển đổi Entity sang DTO (delegate cho PromotionMapper).
     */
    public PromotionDto mapToDto(Promotion p) {
        return promotionMapper.toDto(p);
    }
}
