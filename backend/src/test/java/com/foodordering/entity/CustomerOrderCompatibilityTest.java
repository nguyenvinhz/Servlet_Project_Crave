package com.foodordering.entity;

import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PromotionStatus;
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
        promotion.setStatus(PromotionStatus.ACTIVE);
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
    void persistenceMappingsKeepVoucherWritableAndOrderTimeImmutable() throws Exception {
        CustomerOrder order = new CustomerOrder();
        order.setPromotionId("KM01");
        assertEquals("KM01", order.getPromotionId());

        // A transient replacement would omit the voucher FK when the order is persisted.
        Field promotionId = CustomerOrder.class.getDeclaredField("promotionId");
        Column column = promotionId.getAnnotation(Column.class);
        assertNotNull(column);
        assertEquals("promotion_id", column.name());
        assertTrue(column.insertable());
        assertTrue(column.updatable());
        assertNull(promotionId.getAnnotation(Transient.class));

        Column orderTime = CustomerOrder.class.getDeclaredField("orderedAt").getAnnotation(Column.class);
        assertNotNull(orderTime);
        assertEquals("ordered_at", orderTime.name());
        assertTrue(orderTime.insertable());
        assertFalse(orderTime.updatable());
    }

    @Test
    void persistenceCallbackDefaultsMissingTimeStatusFulfillmentAndAmounts() throws Exception {
        assertNotNull(CustomerOrder.class.getDeclaredMethod("onCreate").getAnnotation(PrePersist.class));
        CustomerOrder order = new CustomerOrder();
        order.setOrderedAt(null);
        order.setStatus(null);
        order.setFulfillmentType(null);
        // Field access is how JPA hydrates this entity, bypassing the normalizing setters.
        for (String name : List.of("subtotal", "discountAmount", "deliveryFee")) {
            Field amount = CustomerOrder.class.getDeclaredField(name);
            amount.setAccessible(true);
            amount.set(order, null);
        }

        LocalDateTime before = LocalDateTime.now();
        order.onCreate();
        LocalDateTime after = LocalDateTime.now();

        assertFalse(order.getOrderedAt().isBefore(before));
        assertFalse(order.getOrderedAt().isAfter(after));
        assertEquals(OrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertEquals(FulfillmentType.DELIVERY, order.getFulfillmentType());
        for (String name : List.of("subtotal", "discountAmount", "deliveryFee")) {
            Field amount = CustomerOrder.class.getDeclaredField(name);
            amount.setAccessible(true);
            assertEquals(BigDecimal.ZERO, amount.get(order));
        }
    }

    @Test
    void persistenceCallbackPreservesExplicitOrderValues() {
        LocalDateTime orderedAt = LocalDateTime.of(2026, 10, 3, 12, 0);
        CustomerOrder order = new CustomerOrder("DH01", new Customer("KH01"),
                new BigDecimal("150000"), orderedAt);
        order.setStatus(OrderStatus.PREPARING);
        order.setFulfillmentType(FulfillmentType.PICKUP);
        order.setDiscountAmount(new BigDecimal("10000"));
        order.setDeliveryFee(new BigDecimal("20000"));

        order.onCreate();

        assertEquals(orderedAt, order.getOrderedAt());
        assertEquals(OrderStatus.PREPARING, order.getStatus());
        assertEquals(FulfillmentType.PICKUP, order.getFulfillmentType());
        assertEquals(new BigDecimal("150000"), order.getSubtotal());
        assertEquals(new BigDecimal("10000"), order.getDiscountAmount());
        assertEquals(new BigDecimal("20000"), order.getDeliveryFee());
    }

    @Test
    void replacingAndClearingCustomerSynchronizesItsId() {
        CustomerOrder order = new CustomerOrder("DH01", new Customer("KH01"), LocalDateTime.now());
        Customer replacement = new Customer("KH02");

        order.setCustomer(replacement);
        assertSame(replacement, order.getCustomer());
        assertEquals("KH02", order.getCustomerId());

        order.setCustomer(null);
        assertNull(order.getCustomer());
        assertNull(order.getCustomerId());
    }

    @Test
    void changingCustomerIdReplacesReferenceWithoutChangingOriginalCustomerId() {
        Customer original = new Customer("KH01");
        CustomerOrder order = new CustomerOrder("DH01", original, LocalDateTime.now());

        order.setCustomerId("KH02");

        assertNotSame(original, order.getCustomer());
        assertEquals("KH01", original.getId());
        assertEquals("KH02", order.getCustomer().getId());
        assertEquals("KH02", order.getCustomerId());
    }

    @Test
    void sameCustomerIdPreservesReferenceAndNullClearsIt() {
        Customer customer = new Customer("KH01");
        CustomerOrder order = new CustomerOrder("DH01", customer, LocalDateTime.now());

        order.setCustomerId("KH01");
        assertSame(customer, order.getCustomer());
        assertEquals("KH01", order.getCustomerId());

        order.setCustomerId(null);
        assertNull(order.getCustomer());
        assertNull(order.getCustomerId());
        assertEquals("KH01", customer.getId());
    }

    @Test
    void changingPromotionIdIsAuthoritativeAndClearsIncompatibleReference() {
        Promotion promotion = new Promotion();
        promotion.setPromotionId("KM01");
        CustomerOrder order = new CustomerOrder();
        order.setPromotion(promotion);

        order.setPromotionId("KM02");

        assertEquals("KM02", order.getPromotionId());
        assertNull(order.getPromotion());
        assertEquals("KM01", promotion.getPromotionId());
    }

    @Test
    void samePromotionIdPreservesReferenceAndNullClearsBothValues() {
        Promotion promotion = new Promotion();
        promotion.setPromotionId("KM01");
        CustomerOrder order = new CustomerOrder();
        order.setPromotion(promotion);

        order.setPromotionId("KM01");
        assertSame(promotion, order.getPromotion());
        assertEquals("KM01", order.getPromotionId());

        order.setPromotionId(null);
        assertNull(order.getPromotion());
        assertNull(order.getPromotionId());
        assertEquals("KM01", promotion.getPromotionId());
    }

    @Test
    void replacingAndClearingPromotionSynchronizesItsPersistentId() {
        Promotion original = new Promotion();
        original.setPromotionId("KM01");
        Promotion replacement = new Promotion();
        replacement.setPromotionId("KM02");
        CustomerOrder order = new CustomerOrder();
        order.setPromotion(original);

        order.setPromotion(replacement);
        assertSame(replacement, order.getPromotion());
        assertEquals("KM02", order.getPromotionId());

        order.setPromotion(null);
        assertNull(order.getPromotion());
        assertNull(order.getPromotionId());
        assertEquals("KM01", original.getPromotionId());
        assertEquals("KM02", replacement.getPromotionId());
    }
}
