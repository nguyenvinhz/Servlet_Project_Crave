package com.foodordering.service;

import com.foodordering.bean.PromotionValidationResultBean;
import com.foodordering.bean.ValidatePromotionForm;
import com.foodordering.model.Customer;
import com.foodordering.model.CustomerOrder;
import com.foodordering.model.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.dao.PromotionDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionServiceTest {
    private PromotionDAO repository;
    private CartService cartService;
    private PromotionService service;
    private Promotion promotion;

    @BeforeEach
    void setUp() {
        repository = mock(PromotionDAO.class);
        cartService = mock(CartService.class);
        service = new PromotionService(repository, cartService);
        promotion = new Promotion();
        promotion.setPromotionId("KM01");
        promotion.setCode("SAVE20K");
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setDiscountType(DiscountType.FIXED_AMOUNT);
        promotion.setDiscountValue(new BigDecimal("20000"));
        promotion.setMinimumOrderValue(new BigDecimal("100000"));
        when(repository.findByCode("SAVE20K")).thenReturn(promotion);
    }

    @Test
    void inflatedClientSubtotalCannotQualifyAnUnderMinimumCart() {
        when(cartService.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("80000"));

        PromotionValidationResultBean result = service.validatePromotion(request("800000"), "KH01");

        assertFalse(result.isValid());
        assertEquals(ErrorCode.PROMOTION_MIN_ORDER_NOT_MET.getCode(), result.getErrorCode());
        verify(repository, never()).calculateDiscountViaDatabase(anyString(), any(), any());
    }

    @Test
    void authenticatedValidationUsesVerifiedCartForDiscountAndResponse() {
        when(cartService.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("150000"));

        PromotionValidationResultBean result = service.validatePromotion(request("999999"), "KH01");

        assertTrue(result.isValid());
        assertEquals(new BigDecimal("150000"), result.getOriginalSubtotal());
        assertEquals(new BigDecimal("20000"), result.getDiscountAmount());
        assertEquals(new BigDecimal("130000"), result.getFinalSubtotal());
        verify(repository).calculateDiscountViaDatabase(eq("KM01"), eq(new BigDecimal("150000")), any());
    }

    @Test
    void requestWithoutCustomerCannotValidateUsingClientSubtotal() {
        PromotionValidationResultBean result = service.validatePromotion(request("800000"), null);

        assertFalse(result.isValid());
        verifyNoInteractions(cartService);
    }

    @Test
    void orderValidationUsesHistoricalSnapshotAndTimestampWithoutReadingLiveCart() {
        LocalDateTime orderTime = LocalDateTime.of(2026, 10, 6, 10, 0);
        promotion.setStartAt(orderTime.minusHours(1));
        promotion.setEndAt(orderTime.plusHours(1));
        CustomerOrder order = new CustomerOrder("DH01", new Customer("KH01"), new BigDecimal("150000"), orderTime);

        PromotionValidationResultBean result = service.validatePromotionForOrder("SAVE20K", order);

        assertTrue(result.isValid());
        assertEquals(new BigDecimal("150000"), result.getOriginalSubtotal());
        verify(repository).calculateDiscountViaDatabase("KM01", new BigDecimal("150000"), orderTime);
        verifyNoInteractions(cartService);
    }

    @Test
    void zeroOrderSnapshotDoesNotBorrowSubtotalFromCurrentCart() {
        CustomerOrder order = new CustomerOrder("DH01", new Customer("KH01"), BigDecimal.ZERO, LocalDateTime.now());

        assertFalse(service.validatePromotionForOrder("SAVE20K", order).isValid());
        verifyNoInteractions(cartService);
    }

    @Test
    void repeatedValidationRechecksTheCartInsteadOfReusingTheEarlierSubtotal() {
        when(cartService.getVerifiedSubtotal("KH01"))
                .thenReturn(new BigDecimal("150000"), new BigDecimal("80000"));
        ValidatePromotionForm request = request("150000");

        assertTrue(service.validatePromotion(request, "KH01").isValid());
        PromotionValidationResultBean result = service.validatePromotion(request, "KH01");

        assertFalse(result.isValid());
        assertEquals(ErrorCode.PROMOTION_MIN_ORDER_NOT_MET.getCode(), result.getErrorCode());
        verify(cartService, times(2)).getVerifiedSubtotal("KH01");
        verify(repository, times(2)).findByCode("SAVE20K");
        verify(repository, times(1)).calculateDiscountViaDatabase(anyString(), any(), any());
    }

    @Test
    void repeatedValidationRejectsAVoucherDisabledAfterItWasApplied() {
        when(cartService.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("150000"));
        assertTrue(service.validatePromotion(request("150000"), "KH01").isValid());
        promotion.setStatus(PromotionStatus.INACTIVE);

        PromotionValidationResultBean result = service.validatePromotion(request("150000"), "KH01");

        assertFalse(result.isValid());
        assertEquals(ErrorCode.PROMOTION_INACTIVE.getCode(), result.getErrorCode());
        verify(repository, times(2)).findByCode("SAVE20K");
        verify(repository, times(1)).calculateDiscountViaDatabase(anyString(), any(), any());
    }

    @Test
    void repeatedValidationRejectsAVoucherThatHasExpiredSinceItWasApplied() {
        when(cartService.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("150000"));
        promotion.setStartAt(LocalDateTime.now().minusDays(1));
        promotion.setEndAt(LocalDateTime.now().plusDays(1));
        assertTrue(service.validatePromotion(request("150000"), "KH01").isValid());
        promotion.setEndAt(LocalDateTime.now().minusSeconds(1));

        PromotionValidationResultBean result = service.validatePromotion(request("150000"), "KH01");

        assertFalse(result.isValid());
        assertEquals(ErrorCode.PROMOTION_EXPIRED.getCode(), result.getErrorCode());
        verify(repository, times(1)).calculateDiscountViaDatabase(anyString(), any(), any());
    }

    @Test
    void orderValidationIncludesExactStartAndEndTimesAndRejectsOutsideTheWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 6, 10, 0);
        LocalDateTime end = start.plusHours(1);
        promotion.setStartAt(start);
        promotion.setEndAt(end);
        CustomerOrder order = new CustomerOrder("DH01", new Customer("KH01"), new BigDecimal("150000"), start);

        assertTrue(service.validatePromotionForOrder("SAVE20K", order).isValid());
        order.setOrderTime(end);
        assertTrue(service.validatePromotionForOrder("SAVE20K", order).isValid());
        order.setOrderTime(start.minusSeconds(1));
        assertEquals(ErrorCode.PROMOTION_NOT_STARTED.getCode(),
                service.validatePromotionForOrder("SAVE20K", order).getErrorCode());
        order.setOrderTime(end.plusSeconds(1));
        assertEquals(ErrorCode.PROMOTION_EXPIRED.getCode(),
                service.validatePromotionForOrder("SAVE20K", order).getErrorCode());
        verifyNoInteractions(cartService);
        verify(repository, times(2)).calculateDiscountViaDatabase(anyString(), any(), any());
    }

    @Test
    void missingOrderCannotQualifyForAVoucherWithNoMinimum() {
        promotion.setMinimumOrderValue(BigDecimal.ZERO);

        PromotionValidationResultBean result = service.validatePromotionForOrder("SAVE20K", null);

        assertFalse(result.isValid());
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), result.getErrorCode());
        verifyNoInteractions(cartService, repository);
    }

    @Test
    void databaseResultTakesPrecedenceIncludingZeroDiscount() {
        LocalDateTime orderTime = LocalDateTime.of(2026, 10, 6, 10, 0);
        when(repository.calculateDiscountViaDatabase("KM01", new BigDecimal("150000"), orderTime))
                .thenReturn(BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, service.calculateDiscount(promotion, new BigDecimal("150000"), orderTime));
    }

    @Test
    void fallbackRoundsPercentageAndAppliesMaximumAndSubtotalCaps() {
        promotion.setDiscountType(DiscountType.PERCENT);
        promotion.setDiscountValue(new BigDecimal("10"));
        assertEquals(new BigDecimal("10001"), service.calculateDiscount(promotion, new BigDecimal("100005"), null));

        promotion.setMaximumDiscount(new BigDecimal("5000"));
        assertEquals(new BigDecimal("5000"), service.calculateDiscount(promotion, new BigDecimal("100005"), null));

        promotion.setMaximumDiscount(null);
        promotion.setDiscountType(DiscountType.FIXED_AMOUNT);
        promotion.setDiscountValue(new BigDecimal("20000"));
        assertEquals(new BigDecimal("10000"), service.calculateDiscount(promotion, new BigDecimal("10000"), null));
    }

    private ValidatePromotionForm request(String subtotal) {
        ValidatePromotionForm request = new ValidatePromotionForm();
        request.setCode("SAVE20K");
        request.setSubtotal(new BigDecimal(subtotal));
        return request;
    }
}
