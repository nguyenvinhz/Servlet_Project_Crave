package com.foodordering.service;

import com.foodordering.bean.PromotionBean;
import com.foodordering.bean.PromotionValidationResultBean;
import com.foodordering.bean.ValidatePromotionForm;
import com.foodordering.model.CustomerOrder;
import com.foodordering.model.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.dao.PromotionDAO;
import com.foodordering.validator.PromotionValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

public class PromotionService {

    private final PromotionDAO promotionDAO;
    private final CartService cartService;

    public PromotionService() {
        this.promotionDAO = new PromotionDAO();
        this.cartService = new CartService();
    }

    public PromotionService(PromotionDAO promotionDAO, CartService cartService) {
        this.promotionDAO = promotionDAO;
        this.cartService = cartService;
    }


    /**
     * Nhận trực tiếp bean ValidatePromotionForm từ Servlet/Controller để xử lý xác thực voucher.
     */
    public PromotionValidationResultBean validatePromotion(ValidatePromotionForm request, String customerId) {
        if (request == null) {
            return new PromotionValidationResultBean(false, "Dữ liệu yêu cầu không được để trống", ErrorCode.BAD_REQUEST.getCode());
        }
        BigDecimal verifiedSubtotal = customerId != null && !customerId.isBlank()
                ? cartService.getVerifiedSubtotal(customerId)
                : BigDecimal.ZERO;
        return validatePromotionAtSubtotal(request.getCode(), verifiedSubtotal, LocalDateTime.now());
    }

    /**
     * Xác thực voucher cho một đơn hàng cụ thể của khách hàng bằng cách lấy orderTime từ CustomerOrder.
     */
    public PromotionValidationResultBean validatePromotionForOrder(String code, CustomerOrder order) {
        if (order == null) {
            return new PromotionValidationResultBean(false, "Đơn hàng không được để trống", ErrorCode.BAD_REQUEST.getCode());
        }
        BigDecimal subtotal = order.getSubtotal();
        LocalDateTime orderTime = order.getOrderTime(); // Lấy orderTime của khách hàng từ CustomerOrder
        return validatePromotionAtSubtotal(code, subtotal, orderTime);
    }

    /**
     * Xác thực voucher và tính toán số tiền giảm giá một cách an toàn trên server:
     * - Nếu client không truyền subtotal hoặc truyền giá trị <= 0, lấy subtotal thật từ giỏ hàng hiện tại của customerId.
     * - Kiểm tra hạn sử dụng theo orderTime, trạng thái kích hoạt, giá trị đơn tối thiểu.
     * - Tính toán chính xác theo loại % (có trần maximum_discount) hoặc số tiền cố định.
     */
    public PromotionValidationResultBean validatePromotion(String code, String customerId, BigDecimal requestedSubtotal, LocalDateTime orderTime) {
        BigDecimal subtotal = requestedSubtotal;
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            subtotal = customerId != null && !customerId.isBlank()
                    ? cartService.getVerifiedSubtotal(customerId)
                    : BigDecimal.ZERO;
        }
        return validatePromotionAtSubtotal(code, subtotal, orderTime);
    }

    private PromotionValidationResultBean validatePromotionAtSubtotal(String code, BigDecimal subtotal, LocalDateTime orderTime) {
        try {
            PromotionValidator.validatePromotionCode(code);
        } catch (Exception e) {
            return new PromotionValidationResultBean(false, e.getMessage(), ErrorCode.BAD_REQUEST.getCode());
        }

        // 1. Tìm thông tin mã khuyến mãi trong DB (DAO trả về Entity)
        Promotion promotion = promotionDAO.findByCode(code.trim().toUpperCase());
        if (promotion == null) {
            return new PromotionValidationResultBean(false, "Mã khuyến mãi không tồn tại", ErrorCode.PROMOTION_NOT_FOUND.getCode());
        }

        subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        LocalDateTime effectiveOrderTime = orderTime != null ? orderTime : LocalDateTime.now();

        // 3. Kiểm tra điều kiện áp dụng với orderTime
        try {
            PromotionValidator.validateApplicable(promotion, subtotal, effectiveOrderTime);
        } catch (PromotionValidationException e) {
            return new PromotionValidationResultBean(false, e.getMessage(), e.getErrorCode().getCode());
        }

        // 4. Tính toán số tiền được giảm
        BigDecimal discountAmount = calculateDiscount(promotion, subtotal, effectiveOrderTime);
        BigDecimal finalSubtotal = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        // Tạo bean hiển thị khuyến mãi
        PromotionBean dto = new PromotionBean(promotion);
        return new PromotionValidationResultBean(true, "Áp dụng mã khuyến mãi thành công", dto, subtotal, discountAmount, finalSubtotal);
    }

    /**
     * Thuật toán tính toán số tiền giảm giá chuẩn xác theo quy tắc trong schema database.
     */
    public BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal, LocalDateTime orderTime) {
        if (promotion == null || subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // Thử tính qua procedure của MySQL trước để đảm bảo tính đồng nhất 100% với DB trigger
        BigDecimal procDiscount = promotionDAO.calculateDiscountViaDatabase(promotion.getPromotionId(), subtotal, orderTime);
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
    public List<PromotionBean> getActivePromotions() {
        List<Promotion> promotions = promotionDAO.findAllActive(LocalDateTime.now());
        return promotions.stream().map(PromotionBean::new).toList();
    }

    /**
     * Lấy thông tin chi tiết của một mã khuyến mãi theo mã code.
     */
    public PromotionBean getPromotionByCode(String code) {
        Promotion p = promotionDAO.findByCode(code);
        return p != null ? new PromotionBean(p) : null;
    }

    /**
     * Tạo bean khuyến mãi cho JSP.
     */
    public PromotionBean mapToBean(Promotion p) {
        return new PromotionBean(p);
    }
}
