package com.foodordering;

import com.foodordering.dto.*;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.service.PromotionService;
import com.foodordering.utils.JsonUtils;
import com.foodordering.validator.PromotionValidator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kiểm thử đơn vị các hàm nghiệp vụ trọng tâm của Cart và Promotion (Ung Văn Trí).
 */
public class CartPromotionServiceTest {

    public static void main(String[] args) {
        System.out.println("=== BẮT ĐẦU KIỂM THỬ BACKEND CART & PROMOTION ===");

        testCartTotalsCalculation();
        testPercentDiscountCalculation();
        testPercentDiscountWithMaxCap();
        testFixedAmountDiscountCalculation();
        testPromotionMinimumOrderValidation();
        testPromotionExpiredValidation();
        testJsonUtilsApiResponse();

        System.out.println("=== TẤT CẢ KIỂM THỬ ĐÃ CHẠY THÀNH CÔNG (PASSED) ===");
    }

    private static void testCartTotalsCalculation() {
        System.out.println("-> Test 1: Tính toán thành tiền giỏ hàng phía server");
        CartItemDto item = new CartItemDto();
        item.setBasePrice(new BigDecimal("45000")); // MA01: Cơm gà xối mỡ: 45,000
        item.setQuantity(2);

        CartItemOptionDto opt1 = new CartItemOptionDto("TC03", "Thêm trứng ốp la", "TOPPING", new BigDecimal("8000"));
        item.setOptions(List.of(opt1));
        item.calculateTotals();

        // Đơn giá: 45000 + 8000 = 53000
        // Thành tiền dòng: 53000 * 2 = 106000
        assert item.getUnitPrice().compareTo(new BigDecimal("53000")) == 0 : "Sai đơn giá: " + item.getUnitPrice();
        assert item.getLineTotal().compareTo(new BigDecimal("106000")) == 0 : "Sai thành tiền: " + item.getLineTotal();

        CartDto cart = new CartDto("GH01", "KH01");
        cart.setItems(List.of(item));
        assert cart.getTotalItems() == 2 : "Sai tổng số lượng: " + cart.getTotalItems();
        assert cart.getSubtotal().compareTo(new BigDecimal("106000")) == 0 : "Sai subtotal: " + cart.getSubtotal();
        System.out.println("   [PASSED] Đơn giá 53,000 đ, Line total 106,000 đ, Cart subtotal 106,000 đ");
    }

    private static void testPercentDiscountCalculation() {
        System.out.println("-> Test 2: Tính giảm giá PERCENT (WELCOME10 - 10% đơn)");
        Promotion p = new Promotion();
        p.setCode("WELCOME10");
        p.setDiscountType(DiscountType.PERCENT);
        p.setDiscountValue(new BigDecimal("10")); // 10%
        p.setMinimumOrderValue(BigDecimal.ZERO);
        p.setMaximumDiscount(new BigDecimal("50000"));

        PromotionService service = new PromotionService();
        BigDecimal subtotal = new BigDecimal("200000"); // 200,000 đ
        BigDecimal discount = service.calculateDiscount(p, subtotal, LocalDateTime.now());

        // 10% của 200,000 = 20,000 đ (nhỏ hơn trần 50,000 đ)
        assert discount.compareTo(new BigDecimal("20000")) == 0 : "Sai giảm giá: " + discount;
        System.out.println("   [PASSED] Subtotal 200,000 đ giảm 10% = 20,000 đ");
    }

    private static void testPercentDiscountWithMaxCap() {
        System.out.println("-> Test 3: Tính giảm giá PERCENT có áp trần tối đa (WELCOME10 - max 50k)");
        Promotion p = new Promotion();
        p.setCode("WELCOME10");
        p.setDiscountType(DiscountType.PERCENT);
        p.setDiscountValue(new BigDecimal("10")); // 10%
        p.setMinimumOrderValue(BigDecimal.ZERO);
        p.setMaximumDiscount(new BigDecimal("50000"));

        PromotionService service = new PromotionService();
        BigDecimal subtotal = new BigDecimal("800000"); // 800,000 đ (10% = 80,000 đ > trần 50,000 đ)
        BigDecimal discount = service.calculateDiscount(p, subtotal, LocalDateTime.now());

        assert discount.compareTo(new BigDecimal("50000")) == 0 : "Chưa áp trần tối đa: " + discount;
        System.out.println("   [PASSED] Subtotal 800,000 đ giảm 10% chặn trần = 50,000 đ");
    }

    private static void testFixedAmountDiscountCalculation() {
        System.out.println("-> Test 4: Tính giảm giá FIXED_AMOUNT (SAVE20K - giảm 20k cho đơn từ 100k)");
        Promotion p = new Promotion();
        p.setCode("SAVE20K");
        p.setDiscountType(DiscountType.FIXED_AMOUNT);
        p.setDiscountValue(new BigDecimal("20000"));
        p.setMinimumOrderValue(new BigDecimal("100000"));

        PromotionService service = new PromotionService();
        BigDecimal subtotal = new BigDecimal("150000");
        BigDecimal discount = service.calculateDiscount(p, subtotal, LocalDateTime.now());

        assert discount.compareTo(new BigDecimal("20000")) == 0 : "Sai giảm cố định: " + discount;
        System.out.println("   [PASSED] Subtotal 150,000 đ giảm cố định = 20,000 đ");
    }

    private static void testPromotionMinimumOrderValidation() {
        System.out.println("-> Test 5: Xác thực đơn hàng chưa đạt giá trị tối thiểu (SAVE20K yêu cầu >= 100k)");
        Promotion p = new Promotion();
        p.setCode("SAVE20K");
        p.setStatus(PromotionStatus.ACTIVE);
        p.setStartAt(LocalDateTime.now().minusDays(1));
        p.setEndAt(LocalDateTime.now().plusDays(1));
        p.setDiscountType(DiscountType.FIXED_AMOUNT);
        p.setDiscountValue(new BigDecimal("20000"));
        p.setMinimumOrderValue(new BigDecimal("100000"));

        BigDecimal smallSubtotal = new BigDecimal("80000"); // 80,000 < 100,000
        boolean caught = false;
        try {
            PromotionValidator.validateApplicable(p, smallSubtotal, LocalDateTime.now());
        } catch (PromotionValidationException e) {
            caught = true;
            assert e.getErrorCode() == ErrorCode.PROMOTION_MIN_ORDER_NOT_MET : "Sai mã lỗi: " + e.getErrorCode();
        }
        assert caught : "Không chặn được đơn dưới mức tối thiểu";
        System.out.println("   [PASSED] Chặn chính xác đơn hàng 80,000 đ chưa đạt mức 100,000 đ");
    }

    private static void testPromotionExpiredValidation() {
        System.out.println("-> Test 6: Xác thực mã khuyến mãi hết hạn");
        Promotion p = new Promotion();
        p.setCode("OPENING30");
        p.setStatus(PromotionStatus.ACTIVE);
        p.setStartAt(LocalDateTime.now().minusDays(10));
        p.setEndAt(LocalDateTime.now().minusDays(1)); // Đã hết hạn hôm qua
        p.setMinimumOrderValue(BigDecimal.ZERO);

        boolean caught = false;
        try {
            PromotionValidator.validateApplicable(p, new BigDecimal("100000"), LocalDateTime.now());
        } catch (PromotionValidationException e) {
            caught = true;
            assert e.getErrorCode() == ErrorCode.PROMOTION_EXPIRED : "Sai mã lỗi hết hạn: " + e.getErrorCode();
        }
        assert caught : "Không phát hiện mã hết hạn";
        System.out.println("   [PASSED] Chặn chính xác mã khuyến mãi đã hết hạn");
    }

    private static void testJsonUtilsApiResponse() {
        System.out.println("-> Test 7: Định dạng chuẩn JSON ApiResponse theo convention của Ung Văn Trí");
        ApiResponse<String> resp = ApiResponse.success("Dữ liệu test", "OK");
        String json = JsonUtils.toJson(resp);
        assert json.contains("\"success\":true") : "JSON thiếu trường success";
        assert json.contains("\"message\":\"Dữ liệu test\"") : "JSON sai message";
        assert json.contains("\"data\":\"OK\"") : "JSON sai data";
        assert json.contains("\"timestamp\":") : "JSON thiếu timestamp";
        System.out.println("   [PASSED] JSON ApiResponse sinh ra chuẩn xác: " + json);
    }
}
