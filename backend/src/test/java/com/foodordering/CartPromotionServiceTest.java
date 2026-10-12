package com.foodordering;

import com.foodordering.dto.*;
import com.foodordering.entity.*;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị các hàm nghiệp vụ trọng tâm của Cart và Promotion (Ung Văn Trí).
 */
public class CartPromotionServiceTest {
    @Test
    void testCartTotalsCalculation() {
        CartItemDto item = new CartItemDto();
        item.setBasePrice(new BigDecimal("45000")); // MA01: Cơm gà xối mỡ: 45,000
        item.setQuantity(2);

        CartItemOptionDto opt1 = new CartItemOptionDto("TC03", "Thêm trứng ốp la", "TOPPING", new BigDecimal("8000"));
        item.setOptions(List.of(opt1));

        CartService cartService = new CartService();
        cartService.calculateItemTotals(item);

        // Đơn giá: 45000 + 8000 = 53000
        // Thành tiền dòng: 53000 * 2 = 106000
        assertEquals(0, item.getUnitPrice().compareTo(new BigDecimal("53000")), "Sai đơn giá: " + item.getUnitPrice());
        assertEquals(0, item.getLineTotal().compareTo(new BigDecimal("106000")), "Sai thành tiền: " + item.getLineTotal());

        CartDto cart = new CartDto("GH01", "KH01");
        cart.setItems(List.of(item));
        cartService.calculateCartTotals(cart);
        assertEquals(2, cart.getTotalItems(), "Sai tổng số lượng: " + cart.getTotalItems());
        assertEquals(0, cart.getSubtotal().compareTo(new BigDecimal("106000")), "Sai subtotal: " + cart.getSubtotal());
    }

    @Test
    void testPercentDiscountCalculation() {
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
        assertEquals(0, discount.compareTo(new BigDecimal("20000")), "Sai giảm giá: " + discount);
    }

    @Test
    void testPercentDiscountWithMaxCap() {
        Promotion p = new Promotion();
        p.setCode("WELCOME10");
        p.setDiscountType(DiscountType.PERCENT);
        p.setDiscountValue(new BigDecimal("10")); // 10%
        p.setMinimumOrderValue(BigDecimal.ZERO);
        p.setMaximumDiscount(new BigDecimal("50000"));

        PromotionService service = offlinePromotionService();
        BigDecimal subtotal = new BigDecimal("800000"); // 800,000 đ (10% = 80,000 đ > trần 50,000 đ)
        BigDecimal discount = service.calculateDiscount(p, subtotal, LocalDateTime.now());

        assertEquals(0, discount.compareTo(new BigDecimal("50000")), "Chưa áp trần tối đa: " + discount);
    }

    @Test
    void testFixedAmountDiscountCalculation() {
        Promotion p = new Promotion();
        p.setCode("SAVE20K");
        p.setDiscountType(DiscountType.FIXED_AMOUNT);
        p.setDiscountValue(new BigDecimal("20000"));
        p.setMinimumOrderValue(new BigDecimal("100000"));

        PromotionService service = offlinePromotionService();
        BigDecimal subtotal = new BigDecimal("150000");
        BigDecimal discount = service.calculateDiscount(p, subtotal, LocalDateTime.now());

        assertEquals(0, discount.compareTo(new BigDecimal("20000")), "Sai giảm cố định: " + discount);
    }

    @Test
    void testPromotionMinimumOrderValidation() {
        Promotion p = new Promotion();
        p.setCode("SAVE20K");
        p.setStatus(PromotionStatus.ACTIVE);
        p.setStartAt(LocalDateTime.now().minusDays(1));
        p.setEndAt(LocalDateTime.now().plusDays(1));
        p.setDiscountType(DiscountType.FIXED_AMOUNT);
        p.setDiscountValue(new BigDecimal("20000"));
        p.setMinimumOrderValue(new BigDecimal("100000"));

        BigDecimal smallSubtotal = new BigDecimal("80000"); // 80,000 < 100,000
        PromotionValidationException exception = assertThrows(PromotionValidationException.class,
                () -> PromotionValidator.validateApplicable(p, smallSubtotal, LocalDateTime.now()));
        assertEquals(ErrorCode.PROMOTION_MIN_ORDER_NOT_MET, exception.getErrorCode());
    }

    @Test
    void testPromotionExpiredValidation() {
        Promotion p = new Promotion();
        p.setCode("OPENING30");
        p.setStatus(PromotionStatus.ACTIVE);
        p.setStartAt(LocalDateTime.now().minusDays(10));
        p.setEndAt(LocalDateTime.now().minusDays(1)); // Đã hết hạn hôm qua
        p.setMinimumOrderValue(BigDecimal.ZERO);

        PromotionValidationException exception = assertThrows(PromotionValidationException.class,
                () -> PromotionValidator.validateApplicable(p, new BigDecimal("100000"), LocalDateTime.now()));
        assertEquals(ErrorCode.PROMOTION_EXPIRED, exception.getErrorCode());
    }

    @Test
    void testJsonUtilsApiResponse() {
        ApiResponse<String> resp = ApiResponse.success("OK");
        String json = JsonUtils.toJson(resp);
        assertTrue(json.contains("\"success\":true"), "JSON thiếu trường success");
        assertTrue(json.contains("\"data\":\"OK\""), "JSON sai data");
    }

    @Test
    void testJsonUtilsJacksonDeserialization() {
        String addJson = "{\"foodId\":\"FOOD_001\",\"quantity\":2,\"note\":\"Ít cay\",\"optionIds\":[\"OPT_1\",\"OPT_2\"]}";
        AddToCartRequest addReq = JsonUtils.toDTO(addJson, AddToCartRequest.class);
        assertNotNull(addReq, "AddToCartRequest null");
        assertEquals("FOOD_001", addReq.getFoodId(), "Sai foodId");
        assertEquals(2, addReq.getQuantity(), "Sai quantity");
        assertEquals("Ít cay", addReq.getNote(), "Sai note");
        assertEquals(2, addReq.getOptionIds().size(), "Sai optionIds size");

        String promoJson = "{\"code\":\"DISCOUNT10\",\"subtotal\":120000}";
        ValidatePromotionRequest promoReq = JsonUtils.toDTO(promoJson, ValidatePromotionRequest.class);
        assertNotNull(promoReq, "ValidatePromotionRequest null");
        assertEquals("DISCOUNT10", promoReq.getCode(), "Sai promo code");
        assertEquals(0, new BigDecimal("120000").compareTo(promoReq.getSubtotal()), "Sai subtotal");
    }

    @Test
    void testManualCartAndItemMappers() {
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
        assertNotNull(optDto, "CartItemOptionDto null");
        assertEquals("OPT01", optDto.getOptionId(), "Sai optionId");
        assertEquals("Thêm trứng", optDto.getName(), "Sai option name");
        assertEquals(0, new BigDecimal("7000").compareTo(optDto.getExtraPrice()), "Sai extraPrice");

        // Test CartItemMapper
        CartItemMapper itemMapper = new CartItemMapper(optionMapper);
        CartItemDto itemDto = itemMapper.toDto(item);
        assertNotNull(itemDto, "CartItemDto null");
        assertEquals("CTGH01", itemDto.getCartItemId(), "Sai cartItemId");
        assertEquals("GH01", itemDto.getCartId(), "Sai cartId");
        assertEquals("FOOD01", itemDto.getFoodId(), "Sai foodId");
        assertEquals("Cơm chiên Dương Châu", itemDto.getFoodName(), "Sai foodName");
        assertEquals(0, new BigDecimal("40000").compareTo(itemDto.getBasePrice()), "Sai basePrice");
        assertEquals(1, itemDto.getOptions().size(), "Sai số lượng options");

        // Test CartMapper
        CartMapper cartMapper = new CartMapper(itemMapper);
        CartDto cartDto = cartMapper.toDto(cart, List.of(item));
        assertNotNull(cartDto, "CartDto null");
        assertEquals("GH01", cartDto.getCartId(), "Sai cartId");
        assertEquals("KH01", cartDto.getCustomerId(), "Sai customerId");
        assertEquals(1, cartDto.getItems().size(), "Sai số lượng items trong CartDto");
    }

    @Test
    void testManualPromotionMapper() {
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
        assertNotNull(dto, "PromotionDto null");
        assertEquals("KM01", dto.getPromotionId(), "Sai promotionId");
        assertEquals("WELCOME10", dto.getCode(), "Sai code");
        assertEquals("Giảm giá chào bạn mới", dto.getName(), "Sai name");
        assertTrue(dto.getDescription().contains("Giảm 10% cho đơn từ 50,000 đ, tối đa 20,000 đ"), "Sai description: " + dto.getDescription());

        List<PromotionDto> dtoList = mapper.toDtoList(List.of(promo));
        assertEquals(1, dtoList.size(), "Sai dtoList size");
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

    @Test
    void testCustomerOrderOrderTimeValidation() {
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
        assertEquals(validOrderTime, validOrder.getOrderTime(), "Sai getOrderTime");
        PromotionValidator.validateApplicableForOrder(promo, validOrder);

        // 2. Đơn hàng đặt sau khi khuyến mãi đã hết hạn (lúc 13:00)
        LocalDateTime expiredOrderTime = baseTime.plusHours(3);
        CustomerOrder expiredOrder = new CustomerOrder("DH02", customer, new BigDecimal("150000"), expiredOrderTime);
        PromotionValidationException caughtExpired = assertThrows(PromotionValidationException.class,
                () -> PromotionValidator.validateApplicableForOrder(promo, expiredOrder));
        assertEquals(ErrorCode.PROMOTION_EXPIRED, caughtExpired.getErrorCode());

        // 3. Đơn hàng đặt trước khi khuyến mãi bắt đầu (lúc 07:00)
        LocalDateTime prematureOrderTime = baseTime.minusHours(3);
        CustomerOrder prematureOrder = new CustomerOrder("DH03", customer, new BigDecimal("150000"), prematureOrderTime);
        PromotionValidationException caughtPremature = assertThrows(PromotionValidationException.class,
                () -> PromotionValidator.validateApplicableForOrder(promo, prematureOrder));
        assertEquals(ErrorCode.PROMOTION_NOT_STARTED, caughtPremature.getErrorCode());
    }
}
