package com.foodordering.entity;

import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.OrderStatus;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.validator.PromotionValidator;
import jakarta.persistence.Column;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CustomerOrderCompatibilityTest {

    @Test
    void voucherValidationUsesConstructorSubtotalAndOrderTimeAliases() {
        LocalDateTime orderedAt = LocalDateTime.of(2026, 10, 3, 12, 0);
        Customer customer = new Customer("KH01");
        CustomerOrder order = new CustomerOrder("DH01", customer, new BigDecimal("150000"), orderedAt);
        Promotion promotion = new Promotion();
        promotion.setStartAt(orderedAt.minusDays(1));
        promotion.setEndAt(orderedAt.plusDays(1));
        promotion.setMinimumOrderValue(new BigDecimal("100000"));

        assertEquals("KH01", order.getCustomerId());
        assertSame(customer, order.getCustomer());
        assertEquals(orderedAt, order.getOrderedAt());
        assertEquals(orderedAt, order.getOrderTime());
        assertDoesNotThrow(() -> PromotionValidator.validateApplicableForOrder(promotion, order));

        order.setOrderTime(promotion.getEndAt().plusSeconds(1));
        assertEquals(order.getOrderTime(), order.getOrderedAt());
        PromotionValidationException expired = assertThrows(PromotionValidationException.class,
                () -> PromotionValidator.validateApplicableForOrder(promotion, order));
        assertEquals(ErrorCode.PROMOTION_EXPIRED, expired.getErrorCode());

        order.setOrderedAt(orderedAt);
        order.setSubtotal(new BigDecimal("50000"));
        assertEquals(orderedAt, order.getOrderTime());
        PromotionValidationException tooSmall = assertThrows(PromotionValidationException.class,
                () -> PromotionValidator.validateApplicableForOrder(promotion, order));
        assertEquals(ErrorCode.PROMOTION_MIN_ORDER_NOT_MET, tooSmall.getErrorCode());
    }

    @Test
    void legacyAndCartOrderIdsShareTheSameValue() {
        CustomerOrder order = new CustomerOrder("DH01");
        assertEquals("DH01", order.getId());
        assertEquals("DH01", order.getOrderId());

        order.setId("DH02");
        assertEquals("DH02", order.getOrderId());
        order.setOrderId("DH03");
        assertEquals("DH03", order.getId());

        order.setCustomer(new Customer("KH02"));
        assertEquals("KH02", order.getCustomerId());
        order.setCustomer(null);
        assertNull(order.getCustomerId());
    }

    @Test
    void orderHelpersKeepDetailsHistoryAndPaymentLinkedToTheirOrder() {
        CustomerOrder order = new CustomerOrder();
        OrderDetail detail = new OrderDetail();
        OrderStatusHistory history = new OrderStatusHistory();
        Payment payment = new Payment();

        order.addOrderDetail(detail);
        order.addStatusHistory(history);
        order.setPaymentHelper(payment);

        assertEquals(List.of(detail), order.getOrderDetails());
        assertSame(order, detail.getOrder());
        assertEquals(List.of(history), order.getStatusHistories());
        assertSame(order, history.getOrder());
        assertSame(payment, order.getPayment());
        assertSame(order, payment.getOrder());
    }

    @Test
    void promotionIdRemainsAWritablePersistentColumn() throws Exception {
        CustomerOrder order = new CustomerOrder();
        order.setPromotionId("KM01");
        assertEquals("KM01", order.getPromotionId());

        // A transient replacement would pass getter tests but omit the voucher FK on insert.
        Field promotionId = CustomerOrder.class.getDeclaredField("promotionId");
        Column column = promotionId.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("promotion_id", column.name());
        assertTrue(column.insertable());
        assertTrue(column.updatable());
        assertNull(promotionId.getAnnotation(Transient.class));

        order.setPromotionId(null);
        assertNull(order.getPromotionId());
    }

    @Test
    void persistenceCallbackDefaultsMissingTimeStatusAndAmounts() throws Exception {
        assertNotNull(CustomerOrder.class.getDeclaredMethod("onCreate").getAnnotation(PrePersist.class));
        CustomerOrder order = new CustomerOrder();
        LocalDateTime before = LocalDateTime.now();
        order.onCreate();
        LocalDateTime after = LocalDateTime.now();

        assertFalse(order.getOrderedAt().isBefore(before));
        assertFalse(order.getOrderedAt().isAfter(after));
        assertEquals(OrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertEquals(BigDecimal.ZERO, order.getSubtotal());
        assertEquals(BigDecimal.ZERO, order.getDiscountAmount());
        assertEquals(BigDecimal.ZERO, order.getDeliveryFee());
    }

    @Test
    void persistenceCallbackPreservesExplicitOrderValues() {
        LocalDateTime orderedAt = LocalDateTime.of(2026, 10, 3, 12, 0);
        CustomerOrder order = new CustomerOrder("DH01", new Customer("KH01"),
                new BigDecimal("150000"), orderedAt);
        order.setStatus(OrderStatus.PREPARING);
        order.setDiscountAmount(new BigDecimal("10000"));
        order.setDeliveryFee(new BigDecimal("20000"));

        order.onCreate();

        assertEquals(orderedAt, order.getOrderedAt());
        assertEquals(OrderStatus.PREPARING, order.getStatus());
        assertEquals(new BigDecimal("150000"), order.getSubtotal());
        assertEquals(new BigDecimal("10000"), order.getDiscountAmount());
        assertEquals(new BigDecimal("20000"), order.getDeliveryFee());
    }
}
