package com.foodordering;

import com.foodordering.dto.*;
import com.foodordering.entity.*;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.mapper.CartItemMapper;
import com.foodordering.mapper.CartItemOptionMapper;
import com.foodordering.mapper.CartMapper;
import com.foodordering.mapper.PromotionMapper;
import com.foodordering.repository.PromotionRepository;
import com.foodordering.service.CartService;
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
        testJsonUtilsJacksonDeserialization();
        testManualCartAndItemMappers();
        testManualPromotionMapper();
        testCustomerOrderOrderTimeValidation();

        System.out.println("=== TẤT CẢ KIỂM THỬ ĐÃ CHẠY THÀNH CÔNG (PASSED) ===");
    }

    private static void testCartTotalsCalculation() {
        System.out.println("-> Test 1: Tính toán thành tiền giỏ hàng phía server");
        CartItemDto item = new CartItemDto();
        item.setBasePrice(new BigDecimal("45000")); // MA01: Cơm gà xối mỡ: 45,000
        item.setQuantity(2);

        CartItemOptionDto opt1 = new CartItemOptionDto("TC03", "Thêm trứng ốp la", "TOPPING", new BigDecimal("8000"));
        item.setOptions(List.of(opt1));
        
        CartService cartService = new CartService();
        cartService.calculateItemTotals(item);

        // Đơn giá: 45000 + 8000 = 53000
        // Thành tiền dòng: 53000 * 2 = 106000
        assert item.getUnitPrice().compareTo(new BigDecimal("53000")) == 0 : "Sai đơn giá: " + item.getUnitPrice();
        assert item.getLineTotal().compareTo(new BigDecimal("106000")) == 0 : "Sai thành tiền: " + item.getLineTotal();

        CartDto cart = new CartDto("GH01", "KH01");
        cart.setItems(List.of(item));
        cartService.calculateCartTotals(cart);
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

        PromotionService service = offlinePromotionService();
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

        PromotionService service = offlinePromotionService();
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

        PromotionService service = offlinePromotionService();
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
        System.out.println("-> Test 7: Định dạng chuẩn JSON ApiResponse");
        ApiResponse<String> resp = ApiResponse.success("OK");
        String json = JsonUtils.toJson(resp);
        assert json.contains("\"success\":true") : "JSON thiếu trường success";
        assert json.contains("\"data\":\"OK\"") : "JSON sai data";
        System.out.println("   [PASSED] JSON ApiResponse sinh ra chuẩn xác: " + json);
    }

    private static void testJsonUtilsJacksonDeserialization() {
        System.out.println("-> Test 8: Jackson Deserialization từ JSON body sang DTO (toDTO)");
        String addJson = "{\"foodId\":\"FOOD_001\",\"quantity\":2,\"note\":\"Ít cay\",\"optionIds\":[\"OPT_1\",\"OPT_2\"]}";
        AddToCartRequest addReq = JsonUtils.toDTO(addJson, AddToCartRequest.class);
        assert addReq != null : "AddToCartRequest null";
        assert "FOOD_001".equals(addReq.getFoodId()) : "Sai foodId";
        assert addReq.getQuantity() == 2 : "Sai quantity";
        assert "Ít cay".equals(addReq.getNote()) : "Sai note";
        assert addReq.getOptionIds().size() == 2 : "Sai optionIds size";

        String promoJson = "{\"code\":\"DISCOUNT10\",\"subtotal\":120000}";
        ValidatePromotionRequest promoReq = JsonUtils.toDTO(promoJson, ValidatePromotionRequest.class);
        assert promoReq != null : "ValidatePromotionRequest null";
        assert "DISCOUNT10".equals(promoReq.getCode()) : "Sai promo code";
        assert new BigDecimal("120000").compareTo(promoReq.getSubtotal()) == 0 : "Sai subtotal";
        System.out.println("   [PASSED] Jackson parse JSON body sang DTO (toDTO) chính xác tuyệt đối");
    }

    private static void testManualCartAndItemMappers() {
        System.out.println("-> Test 9: Mapper thủ công Cart & CartItem (không dùng MapStruct)");
        Customer customer = new Customer("KH01");
        Cart cart = new Cart("GH01", customer);

        Food food = new Food("FOOD01", "Cơm chiên Dương Châu", new BigDecimal("40000"));
        food.setImageUrl("/images/com-chien.jpg");

        CartItem item = new CartItem("CTGH01", cart, food, 2, "Không hành");

        FoodOption option = new FoodOption("OPT01", "Thêm trứng", OptionType.TOPPING, new BigDecimal("7000"));
        CartItemOption itemOption = new CartItemOption(item, option);
        item.setItemOptions(List.of(itemOption));

        // Test CartItemOptionMapper
        CartItemOptionMapper optionMapper = new CartItemOptionMapper();
        CartItemOptionDto optDto = optionMapper.toDto(itemOption);
        assert optDto != null : "CartItemOptionDto null";
        assert "OPT01".equals(optDto.getOptionId()) : "Sai optionId";
        assert "Thêm trứng".equals(optDto.getName()) : "Sai option name";
        assert new BigDecimal("7000").compareTo(optDto.getExtraPrice()) == 0 : "Sai extraPrice";

        // Test CartItemMapper
        CartItemMapper itemMapper = new CartItemMapper(optionMapper);
        CartItemDto itemDto = itemMapper.toDto(item);
        assert itemDto != null : "CartItemDto null";
        assert "CTGH01".equals(itemDto.getCartItemId()) : "Sai cartItemId";
        assert "GH01".equals(itemDto.getCartId()) : "Sai cartId";
        assert "FOOD01".equals(itemDto.getFoodId()) : "Sai foodId";
        assert "Cơm chiên Dương Châu".equals(itemDto.getFoodName()) : "Sai foodName";
        assert new BigDecimal("40000").compareTo(itemDto.getBasePrice()) == 0 : "Sai basePrice";
        assert itemDto.getOptions().size() == 1 : "Sai số lượng options";

        // Test CartMapper
        CartMapper cartMapper = new CartMapper(itemMapper);
        CartDto cartDto = cartMapper.toDto(cart, List.of(item));
        assert cartDto != null : "CartDto null";
        assert "GH01".equals(cartDto.getCartId()) : "Sai cartId";
        assert "KH01".equals(cartDto.getCustomerId()) : "Sai customerId";
        assert cartDto.getItems().size() == 1 : "Sai số lượng items trong CartDto";
        System.out.println("   [PASSED] Chuyển đổi Cart/CartItem Entity -> DTO thủ công hoàn toàn chính xác");
    }

    private static void testManualPromotionMapper() {
        System.out.println("-> Test 10: Mapper thủ công Promotion (không dùng MapStruct)");
        Promotion promo = new Promotion();
        promo.setPromotionId("KM01");
        promo.setCode("WELCOME10");
        promo.setName("Giảm giá chào bạn mới");
        promo.setDiscountType(DiscountType.PERCENT);
        promo.setDiscountValue(new BigDecimal("10"));
        promo.setMinimumOrderValue(new BigDecimal("50000"));
        promo.setMaximumDiscount(new BigDecimal("20000"));
        promo.setStatus(PromotionStatus.ACTIVE);

        PromotionMapper mapper = new PromotionMapper();
        PromotionDto dto = mapper.toDto(promo);
        assert dto != null : "PromotionDto null";
        assert "KM01".equals(dto.getPromotionId()) : "Sai promotionId";
        assert "WELCOME10".equals(dto.getCode()) : "Sai code";
        assert "Giảm giá chào bạn mới".equals(dto.getName()) : "Sai name";
        assert dto.getDescription().contains("Giảm 10% cho đơn từ 50,000 đ, tối đa 20,000 đ") : "Sai description: " + dto.getDescription();

        List<PromotionDto> dtoList = mapper.toDtoList(List.of(promo));
        assert dtoList.size() == 1 : "Sai dtoList size";
        System.out.println("   [PASSED] Chuyển đổi Promotion Entity -> DTO thủ công hoàn toàn chính xác");
    }

    private static PromotionService offlinePromotionService() {
        PromotionRepository repository = new PromotionRepository() {
            @Override
            public BigDecimal calculateDiscountViaDatabase(String promotionId, BigDecimal subtotal, LocalDateTime orderTime) {
                return null;
            }
        };
        return new PromotionService(repository, new CartService());
    }

    private static void testCustomerOrderOrderTimeValidation() {
        System.out.println("-> Test 11: Xác thực thời gian đặt hàng (CustomerOrder.orderTime) với hạn của Promotion");
        Promotion promo = new Promotion();
        promo.setPromotionId("KM02");
        promo.setCode("FLASH50");
        promo.setStatus(PromotionStatus.ACTIVE);
        LocalDateTime baseTime = LocalDateTime.of(2026, 10, 6, 10, 0, 0);
        promo.setStartAt(baseTime.minusHours(2)); // Bắt đầu lúc 08:00
        promo.setEndAt(baseTime.plusHours(2));   // Kết thúc lúc 12:00
        promo.setMinimumOrderValue(new BigDecimal("100000"));
        promo.setDiscountType(DiscountType.FIXED_AMOUNT);
        promo.setDiscountValue(new BigDecimal("30000"));

        Customer customer = new Customer("KH01");

        // 1. Đơn hàng đặt trong khung giờ hợp lệ (lúc 09:30)
        LocalDateTime validOrderTime = baseTime.minusMinutes(30);
        CustomerOrder validOrder = new CustomerOrder("DH01", customer, new BigDecimal("150000"), validOrderTime);
        assert validOrder.getOrderTime().equals(validOrderTime) : "Sai getOrderTime";
        PromotionValidator.validateApplicableForOrder(promo, validOrder);

        // 2. Đơn hàng đặt sau khi khuyến mãi đã hết hạn (lúc 13:00)
        LocalDateTime expiredOrderTime = baseTime.plusHours(3);
        CustomerOrder expiredOrder = new CustomerOrder("DH02", customer, new BigDecimal("150000"), expiredOrderTime);
        boolean caughtExpired = false;
        try {
            PromotionValidator.validateApplicableForOrder(promo, expiredOrder);
        } catch (PromotionValidationException e) {
            caughtExpired = true;
            assert e.getErrorCode() == ErrorCode.PROMOTION_EXPIRED : "Sai mã lỗi: " + e.getErrorCode();
        }
        assert caughtExpired : "Không phát hiện đơn hàng đặt sau khi mã hết hạn";

        // 3. Đơn hàng đặt trước khi khuyến mãi bắt đầu (lúc 07:00)
        LocalDateTime prematureOrderTime = baseTime.minusHours(3);
        CustomerOrder prematureOrder = new CustomerOrder("DH03", customer, new BigDecimal("150000"), prematureOrderTime);
        boolean caughtPremature = false;
        try {
            PromotionValidator.validateApplicableForOrder(promo, prematureOrder);
        } catch (PromotionValidationException e) {
            caughtPremature = true;
            assert e.getErrorCode() == ErrorCode.PROMOTION_NOT_STARTED : "Sai mã lỗi: " + e.getErrorCode();
        }
        assert caughtPremature : "Không phát hiện đơn hàng đặt trước khi mã bắt đầu";

        System.out.println("   [PASSED] Xác thực CustomerOrder.orderTime với [startAt, endAt] của Promotion chính xác");
    }
}
