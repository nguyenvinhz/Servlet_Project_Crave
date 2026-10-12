package com.foodordering.service;

import com.foodordering.entity.CustomerOrder;
import com.foodordering.entity.Payment;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentMethod;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.AccountException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.PaymentGatewayException;
import com.foodordering.utils.JsonProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayosPaymentServiceTest {
    private EntityManager em;
    private EntityTransaction tx;
    private PayosGateway gateway;
    private PayosPaymentService service;
    private Payment payment;
    private CustomerOrder order;
    @BeforeEach void setup() {
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        em = mock(EntityManager.class);
        tx = mock(EntityTransaction.class);
        gateway = mock(PayosGateway.class);
        when(factory.createEntityManager()).thenReturn(em);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);
        service = new PayosPaymentService(factory, gateway);
        order = new CustomerOrder("DH01");
        order.setCustomerId("KH01");
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        payment = new Payment();
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setAmount(new BigDecimal("65000"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setOrder(order);
        order.setPayment(payment);
        when(em.find(CustomerOrder.class, "DH01")).thenReturn(order);
    }

    @Test void ownerAndMethodCheckedBeforeAnyPaymentRequest() {
        assertThrows(AccountException.class, () -> service.createLink("OTHER", "DH01"));
        verify(gateway, never()).createOrGet(anyLong(), anyLong(), anyString(), anyBoolean());
        payment.setPaymentMethod(PaymentMethod.CASH);
        assertThrows(BadRequestException.class, () -> service.createLink("KH01", "DH01"));
    }
    @Test void cancelledAndPaidOrdersNeverCreateLinks() {
        order.setStatus(OrderStatus.CANCELLED);
        assertThrows(BadRequestException.class, () -> service.createLink("KH01", "DH01"));
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        payment.setStatus(PaymentStatus.SUCCESS);
        assertThrows(BadRequestException.class, () -> service.createLink("KH01", "DH01"));
        verify(gateway, never()).createOrGet(anyLong(), anyLong(), anyString(), anyBoolean());
    }
    @Test void serverAmountAndStableReservedCodeUsedAcrossRetry() {
        when(gateway.createOrGet(anyLong(), eq(65000L), eq("DH01"), anyBoolean())).thenAnswer(call ->
                new PayosGateway.PaymentLink(call.getArgument(0), 65000L, "link01", "https://pay.payos.vn/web/link01", "PENDING"));
        assertEquals("https://pay.payos.vn/web/link01", service.createLink("KH01", "DH01").checkoutUrl());
        long code = payment.getPayosOrderCode();
        assertTrue(code > 0 && code <= 9007199254740991L);
        service.createLink("KH01", "DH01");
        verify(gateway).createOrGet(code, 65000L, "DH01", false);
        verify(gateway).createOrGet(code, 65000L, "DH01", true);
        assertEquals("link01", payment.getPayosPaymentLinkId());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }
    @Test void interruptedLinkResponsePreservesReservedCodeForSafeRetry() {
        when(gateway.createOrGet(anyLong(), anyLong(), anyString(), anyBoolean()))
                .thenThrow(new PaymentGatewayException("PAYOS_UNAVAILABLE", "unavailable", 502));
        assertThrows(PaymentGatewayException.class, () -> service.createLink("KH01", "DH01"));
        Long code = payment.getPayosOrderCode();
        assertNotNull(code);
        verify(tx).commit();
        assertThrows(PaymentGatewayException.class, () -> service.createLink("KH01", "DH01"));
        verify(gateway).createOrGet(code, 65000L, "DH01", true);
    }
    @Test void rateLimitKeepsSavedOrderPendingAndReservedCodeForUserRetry() {
        when(gateway.createOrGet(anyLong(), anyLong(), anyString(), anyBoolean()))
                .thenThrow(new PaymentGatewayException("PAYOS_RATE_LIMITED", "Retry later", 503, 5));
        PaymentGatewayException error = assertThrows(PaymentGatewayException.class, () -> service.createLink("KH01", "DH01"));
        assertEquals("PAYOS_RATE_LIMITED", error.getErrorCode());
        Long code = payment.getPayosOrderCode();
        assertNotNull(code);
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("DH01", order.getId());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertThrows(PaymentGatewayException.class, () -> service.createLink("KH01", "DH01"));
        verify(gateway).createOrGet(code, 65000L, "DH01", false);
        verify(gateway).createOrGet(code, 65000L, "DH01", true);
    }
    @Test void cancelledUnpaidGatewayLinkCanBeRenewedSafely() {
        reserve();
        when(gateway.createOrGet(eq(42L), eq(65000L), eq("DH01"), eq(true)))
                .thenReturn(new PayosGateway.PaymentLink(42, 65000, "link01", "https://pay.payos.vn/web/link01", "CANCELLED"));
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 0, "link01", "CANCELLED", ""));
        when(gateway.createOrGet(longThat(code -> code != 42), eq(65000L), eq("DH01"), eq(false)))
                .thenAnswer(call -> new PayosGateway.PaymentLink(call.getArgument(0), 65000, "newlink", "https://pay.payos.vn/web/newlink", "PENDING"));
        assertEquals("https://pay.payos.vn/web/newlink", service.createLink("KH01", "DH01").checkoutUrl());
        assertNotEquals(42L, payment.getPayosOrderCode());
        assertEquals("newlink", payment.getPayosPaymentLinkId());
    }
    @Test void signedCancelledSnapshotAvoidsRedundantGatewayLookup() {
        reserve();
        when(gateway.createOrGet(eq(42L), eq(65000L), eq("DH01"), eq(true)))
                .thenReturn(new PayosGateway.PaymentLink(42, 65000, "link01", "https://pay.payos.vn/web/link01", "CANCELLED", 0L));
        when(gateway.createOrGet(longThat(code -> code != 42), eq(65000L), eq("DH01"), eq(false)))
                .thenAnswer(call -> new PayosGateway.PaymentLink(call.getArgument(0), 65000, "newlink", "https://pay.payos.vn/web/newlink", "PENDING"));
        assertEquals("https://pay.payos.vn/web/newlink", service.createLink("KH01", "DH01").checkoutUrl());
        verify(gateway, never()).getPayment(anyLong());
    }
    @Test void staleCancelledSnapshotCannotOverwriteConcurrentRenewal() {
        reserve();
        when(gateway.createOrGet(eq(42L), eq(65000L), eq("DH01"), eq(true))).thenAnswer(call -> {
            payment.setPayosOrderCode(99L);
            payment.setPayosPaymentLinkId("concurrent");
            return new PayosGateway.PaymentLink(42, 65000, "link01", "https://pay.payos.vn/web/link01", "CANCELLED", 0L);
        });
        when(gateway.createOrGet(eq(99L), eq(65000L), eq("DH01"), eq(true)))
                .thenReturn(new PayosGateway.PaymentLink(99, 65000, "concurrent", "https://pay.payos.vn/web/concurrent", "PENDING"));
        assertEquals("https://pay.payos.vn/web/concurrent", service.createLink("KH01", "DH01").checkoutUrl());
        assertEquals(99L, payment.getPayosOrderCode());
        assertEquals("concurrent", payment.getPayosPaymentLinkId());
        verify(gateway, never()).getPayment(anyLong());
    }
    @Test void cancelledLinkWithPartialPaymentCannotCreateAnotherCharge() {
        reserve();
        when(gateway.createOrGet(eq(42L), eq(65000L), eq("DH01"), eq(true)))
                .thenReturn(new PayosGateway.PaymentLink(42, 65000, "link01", "https://pay.payos.vn/web/link01", "CANCELLED"));
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 5000, "link01", "CANCELLED", "TX_PART"));
        assertThrows(BadRequestException.class, () -> service.createLink("KH01", "DH01"));
        assertEquals(42L, payment.getPayosOrderCode());
        verify(gateway, never()).createOrGet(longThat(code -> code != 42), anyLong(), anyString(), anyBoolean());
    }
    @Test void tamperedAmountOrLinkNeverMarksPaymentSuccessful() {
        reserve();
        for (PayosGateway.PaymentState state : List.of(
                new PayosGateway.PaymentState(42, 1, 65000, "link01", "PAID", "TX1"),
                new PayosGateway.PaymentState(42, 65000, 65000, "foreign", "PAID", "TX1"))) {
            when(gateway.getPayment(42)).thenReturn(state);
            assertThrows(PaymentGatewayException.class, () -> service.syncPayment("KH01", "DH01"));
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
        }
    }
    @Test void gatewayPaidStatusStillRequiresFullAmount() {
        reserve();
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 100, "link01", "PAID", "TX1"));
        assertEquals("PENDING", service.syncPayment("KH01", "DH01").status());
        assertNull(payment.getPaidAt());
    }
    @Test void browserReturnReconcilesOnlyAuthenticatedRemotePaidStatus() {
        reserve();
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 0, "link01", "PENDING", ""));
        assertEquals("PENDING", service.syncPayment("KH01", "DH01").status());
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 65000, "link01", "PAID", "TX1"));
        assertEquals("SUCCESS", service.syncPayment("KH01", "DH01").status());
        assertEquals("TX1", payment.getTransactionRef());
        assertNotNull(payment.getPaidAt());
    }
    @Test void duplicateSignedWebhookPreservesPaymentTimeAndReference() {
        reserve();
        setupQuery(List.of(payment));
        var payload = JsonProvider.objectMapper().createObjectNode();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 65000, "link01", "TX1", true));
        service.receiveWebhook(payload);
        LocalDateTime paidAt = payment.getPaidAt();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 65000, "link01", "DUPLICATE", true));
        service.receiveWebhook(payload);
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(paidAt, payment.getPaidAt());
        assertEquals("TX1", payment.getTransactionRef());
    }
    @Test void unknownSignedSampleIsAcknowledgedWithoutPaymentMutation() {
        setupQuery(List.of());
        var payload = JsonProvider.objectMapper().createObjectNode();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(123, 3000, "sample", "SAMPLE", true));
        assertDoesNotThrow(() -> service.receiveWebhook(payload));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(tx).commit();
    }
    @Test void webhookAmountMismatchRollsBackBeforeMutation() {
        reserve();
        setupQuery(List.of(payment));
        var payload = JsonProvider.objectMapper().createObjectNode();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 65001, "link01", "TX1", true));
        assertThrows(PaymentGatewayException.class, () -> service.receiveWebhook(payload));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getPaidAt());
        verify(tx).rollback();
    }
    @Test void callbackBeforeLinkResponseRequiresRemoteLinkConfirmation() {
        reserve();
        payment.setPayosPaymentLinkId(null);
        setupQuery(List.of(payment));
        var payload = JsonProvider.objectMapper().createObjectNode();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 65000, "link01", "TX1", true));
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 65000, "foreign", "PAID", "TX1"));
        assertThrows(PaymentGatewayException.class, () -> service.receiveWebhook(payload));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }
    @Test void validPartialWebhookAcknowledgesPendingUntilGatewayReportsFullSettlement() {
        reserve();
        setupQuery(List.of(payment));
        var payload = JsonProvider.objectMapper().createObjectNode();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 5000, "link01", "TX_PART1", true));
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 5000, "link01", "PENDING", "TX_PART1"));
        service.receiveWebhook(payload);
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getPaidAt());
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 60000, "link01", "TX_PART2", true));
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 65000, "link01", "PAID", "TX_PART1"));
        service.receiveWebhook(payload);
        LocalDateTime paidAt = payment.getPaidAt();
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("TX_PART2", payment.getTransactionRef());
        service.receiveWebhook(payload);
        assertEquals(paidAt, payment.getPaidAt());
        assertEquals(new BigDecimal("65000"), payment.getAmount());
    }
    @Test void overpaymentRequiresGatewayConfirmationAndKeepsExpectedOrderAmount() {
        reserve();
        setupQuery(List.of(payment));
        var payload = JsonProvider.objectMapper().createObjectNode();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 70000, "link01", "TX_OVER", true));
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 65000, "link01", "PAID", "TX_OVER"));
        assertThrows(PaymentGatewayException.class, () -> service.receiveWebhook(payload));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        when(gateway.getPayment(42)).thenReturn(new PayosGateway.PaymentState(42, 65000, 70000, "link01", "PAID", "TX_OVER"));
        service.receiveWebhook(payload);
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(new BigDecimal("65000"), payment.getAmount());
    }
    @Test void partialWebhookCannotSettleForeignOrDifferentAmountGatewayLink() {
        reserve();
        setupQuery(List.of(payment));
        var payload = JsonProvider.objectMapper().createObjectNode();
        when(gateway.verifyWebhook(payload)).thenReturn(new PayosGateway.WebhookPayment(42, 5000, "link01", "TX_PART", true));
        for (var remote : List.of(new PayosGateway.PaymentState(42, 65000, 65000, "foreign", "PAID", "TX_PART"),
                new PayosGateway.PaymentState(42, 1, 65000, "link01", "PAID", "TX_PART"),
                new PayosGateway.PaymentState(99, 65000, 65000, "link01", "PAID", "TX_PART"))) {
            when(gateway.getPayment(42)).thenReturn(remote);
            assertThrows(PaymentGatewayException.class, () -> service.receiveWebhook(payload));
            assertEquals(PaymentStatus.PENDING, payment.getStatus());
        }
    }
    private void reserve() { payment.setPayosOrderCode(42L); payment.setPayosPaymentLinkId("link01"); }
    @SuppressWarnings("unchecked") private void setupQuery(List<Payment> payments) {
        TypedQuery<Payment> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Payment.class))).thenReturn(query);
        when(query.setParameter(eq("code"), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(payments);
    }
}
