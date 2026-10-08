package com.foodordering.validator;

import com.foodordering.entity.CustomerOrder;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.PromotionValidationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PromotionValidator {

    private PromotionValidator() {}

    /**
     * Kiểm tra định dạng mã khuyến mãi.
     */
    public static void validatePromotionCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Mã khuyến mãi không được để trống");
        }
    }

    /**
     * Kiểm tra các điều kiện áp dụng mã khuyến mãi:
     * 1. Mã tồn tại
     * 2. Trạng thái ACTIVE
     * 3. Thời gian orderTime nằm trong khoảng [startAt, endAt]
     * 4. Giá trị đơn hàng (subtotal) >= minimumOrderValue
     */
    public static void validateApplicable(Promotion promotion, BigDecimal subtotal, LocalDateTime orderTime) {
        if (promotion == null) {
            throw new PromotionValidationException(ErrorCode.PROMOTION_NOT_FOUND, "Không tìm thấy mã khuyến mãi");
        }

        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new PromotionValidationException(ErrorCode.PROMOTION_INACTIVE, "Mã khuyến mãi hiện đang bị khóa hoặc ngừng hoạt động");
        }

        LocalDateTime checkTime = orderTime != null ? orderTime : LocalDateTime.now();

        if (promotion.getStartAt() != null && checkTime.isBefore(promotion.getStartAt())) {
            throw new PromotionValidationException(ErrorCode.PROMOTION_NOT_STARTED, "Chương trình khuyến mãi chưa bắt đầu");
        }

        if (promotion.getEndAt() != null && checkTime.isAfter(promotion.getEndAt())) {
            throw new PromotionValidationException(ErrorCode.PROMOTION_EXPIRED, "Mã khuyến mãi đã hết hạn sử dụng");
        }

        BigDecimal currentSubtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        BigDecimal minOrder = promotion.getMinimumOrderValue() != null ? promotion.getMinimumOrderValue() : BigDecimal.ZERO;
        if (currentSubtotal.compareTo(minOrder) < 0) {
            throw new PromotionValidationException(ErrorCode.PROMOTION_MIN_ORDER_NOT_MET,
                    String.format("Mã chỉ áp dụng cho đơn hàng từ %,.0f VNĐ (Đơn hiện tại: %,.0f VNĐ)",
                            minOrder.doubleValue(), currentSubtotal.doubleValue()));
        }
    }

    /**
     * Xác thực tính hợp lệ của mã khuyến mãi dựa trên thông tin CustomerOrder của khách hàng
     * (lấy orderTime và subtotal trực tiếp từ CustomerOrder entity).
     */
    public static void validateApplicableForOrder(Promotion promotion, CustomerOrder order) {
        if (order == null) {
            validateApplicable(promotion, BigDecimal.ZERO, LocalDateTime.now());
            return;
        }
        validateApplicable(promotion, order.getSubtotal(), order.getOrderTime());
    }
}
