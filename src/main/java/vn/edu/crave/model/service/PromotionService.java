package vn.edu.crave.model.service;

import vn.edu.crave.model.viewmodel.PromotionDto;
import vn.edu.crave.model.viewmodel.PromotionValidationResultDto;
import vn.edu.crave.model.form.ValidatePromotionRequest;
import vn.edu.crave.model.entity.CustomerOrder;
import vn.edu.crave.model.entity.Promotion;
import vn.edu.crave.model.enums.DiscountType;
import vn.edu.crave.model.enums.ErrorCode;
import vn.edu.crave.exception.PromotionValidationException;
import vn.edu.crave.model.dao.PromotionDAO;
import vn.edu.crave.model.validator.PromotionValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * Nhận trực tiếp DTO ValidatePromotionRequest từ Servlet/Controller để xử lý xác thực voucher.
     */
    public PromotionValidationResultDto validatePromotion(ValidatePromotionRequest request, String customerId) {
        if (request == null) {
            return new PromotionValidationResultDto(false, "Dữ liệu yêu cầu không được để trống", ErrorCode.BAD_REQUEST.getCode());
        }
        BigDecimal verifiedSubtotal = customerId != null && !customerId.isBlank()
                ? cartService.getVerifiedSubtotal(customerId)
                : BigDecimal.ZERO;
        return validatePromotionAtSubtotal(request.getCode(), verifiedSubtotal, LocalDateTime.now());
    }

    /**
     * Xác thực voucher cho một đơn hàng cụ thể của khách hàng bằng cách lấy orderTime từ CustomerOrder.
     */
    public PromotionValidationResultDto validatePromotionForOrder(String code, CustomerOrder order) {
        if (order == null) {
            return new PromotionValidationResultDto(false, "Đơn hàng không được để trống", ErrorCode.BAD_REQUEST.getCode());
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
    public PromotionValidationResultDto validatePromotion(String code, String customerId, BigDecimal requestedSubtotal, LocalDateTime orderTime) {
        BigDecimal subtotal = requestedSubtotal;
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            subtotal = customerId != null && !customerId.isBlank()
                    ? cartService.getVerifiedSubtotal(customerId)
                    : BigDecimal.ZERO;
        }
        return validatePromotionAtSubtotal(code, subtotal, orderTime);
    }

    private PromotionValidationResultDto validatePromotionAtSubtotal(String code, BigDecimal subtotal, LocalDateTime orderTime) {
        try {
            PromotionValidator.validatePromotionCode(code);
        } catch (Exception e) {
            return new PromotionValidationResultDto(false, e.getMessage(), ErrorCode.BAD_REQUEST.getCode());
        }

        // 1. Tìm thông tin mã khuyến mãi trong DB (Repository trả về Entity)
        Promotion promotion = promotionDAO.findByCode(code.trim().toUpperCase());
        if (promotion == null) {
            return new PromotionValidationResultDto(false, "Mã khuyến mãi không tồn tại", ErrorCode.PROMOTION_NOT_FOUND.getCode());
        }

        subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
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

        PromotionDto dto = createPromotionDto(promotion);
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
    public List<PromotionDto> getActivePromotions() {
        List<Promotion> promotions = promotionDAO.findAllActive(LocalDateTime.now());
        List<PromotionDto> results = new ArrayList<>();
        if (promotions != null) {
            for (Promotion promotion : promotions) {
                if (promotion != null) {
                    results.add(createPromotionDto(promotion));
                }
            }
        }
        return results;
    }

    /**
     * Lấy thông tin chi tiết của một mã khuyến mãi theo mã code.
     */
    public PromotionDto getPromotionByCode(String code) {
        Promotion p = promotionDAO.findByCode(code);
        return createPromotionDto(p);
    }

    private PromotionDto createPromotionDto(Promotion promotion) {
        if (promotion == null) {
            return null;
        }
        PromotionDto dto = new PromotionDto();
        dto.setPromotionId(promotion.getPromotionId());
        dto.setCode(promotion.getCode());
        dto.setName(promotion.getName());
        dto.setDiscountType(promotion.getDiscountType());
        dto.setDiscountValue(promotion.getDiscountValue());
        dto.setMinimumOrderValue(promotion.getMinimumOrderValue());
        dto.setMaximumDiscount(promotion.getMaximumDiscount());
        dto.setStartAt(promotion.getStartAt());
        dto.setEndAt(promotion.getEndAt());
        dto.setStatus(promotion.getStatus());

        if (promotion.getDiscountType() == DiscountType.PERCENT) {
            String maximumDiscount = (promotion.getMaximumDiscount() != null && promotion.getMaximumDiscount().doubleValue() > 0)
                    ? String.format(", tối đa %,.0f đ", promotion.getMaximumDiscount().doubleValue())
                    : "";
            dto.setDescription(String.format("Giảm %s%% cho đơn từ %,.0f đ%s",
                    promotion.getDiscountValue() != null ? promotion.getDiscountValue().toPlainString() : "0",
                    promotion.getMinimumOrderValue() != null ? promotion.getMinimumOrderValue().doubleValue() : 0,
                    maximumDiscount));
        } else {
            dto.setDescription(String.format("Giảm %,.0f đ cho đơn từ %,.0f đ",
                    promotion.getDiscountValue() != null ? promotion.getDiscountValue().doubleValue() : 0,
                    promotion.getMinimumOrderValue() != null ? promotion.getMinimumOrderValue().doubleValue() : 0));
        }
        return dto;
    }
}
