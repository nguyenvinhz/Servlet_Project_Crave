package vn.edu.crave.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.edu.crave.model.entity.CustomerOrder;
import vn.edu.crave.model.entity.Payment;
import vn.edu.crave.model.entity.PayosPaymentLink;
import vn.edu.crave.model.enums.OrderStatus;
import vn.edu.crave.model.enums.PaymentMethod;
import vn.edu.crave.model.enums.PaymentStatus;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.integration.PayosClient;
import vn.edu.crave.integration.PayosException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayosPaymentServiceTest {
    private static final long CODE = 1_234_567_890_123L;
    private static final String LINK_ID = "payos-test-link";
    private final ObjectMapper json = new ObjectMapper();
    private PayosClient client;
    private EntityManager em;
    private EntityTransaction tx;
    private PayosPaymentService service;
    private CustomerOrder order;
    private Payment payment;
    private AtomicReference<PayosPaymentLink> saved;
    private AtomicBoolean inTransaction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(PayosClient.class);
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        em = mock(EntityManager.class);
        tx = mock(EntityTransaction.class);
        inTransaction = new AtomicBoolean();
        saved = new AtomicReference<>();
        when(factory.createEntityManager()).thenReturn(em);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenAnswer(call -> inTransaction.get());
        doAnswer(call -> { inTransaction.set(true); return null; }).when(tx).begin();
        doAnswer(call -> { inTransaction.set(false); return null; }).when(tx).commit();
        doAnswer(call -> { inTransaction.set(false); return null; }).when(tx).rollback();
        order = new CustomerOrder("O123456789");
        order.setCustomerId("C123456789");
        order.setTotalAmount(new BigDecimal("100000"));
        payment = new Payment();
        payment.setId("P12345678901");
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("100000"));
        payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        payment.setStatus(PaymentStatus.PENDING);
        order.setPayment(payment);
        when(em.find(CustomerOrder.class, order.getId())).thenReturn(order);
        when(em.find(CustomerOrder.class, order.getId(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);
        when(em.find(Payment.class, payment.getId(), LockModeType.PESSIMISTIC_WRITE)).thenReturn(payment);
        when(em.find(eq(PayosPaymentLink.class), any())).thenAnswer(call ->
                Long.valueOf(CODE).equals(call.getArgument(1)) ? saved.get() : null);
        TypedQuery<PayosPaymentLink> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PayosPaymentLink.class))).thenReturn(query);
        when(query.setParameter(eq("paymentId"), any())).thenReturn(query);
        when(query.getResultList()).thenAnswer(call -> saved.get() == null ? List.of() : List.of(saved.get()));
        doAnswer(call -> {
            PayosPaymentLink link = call.getArgument(0);
            link.setOrderCode(CODE);
            saved.set(link);
            return null;
        }).when(em).persist(any(PayosPaymentLink.class));
        when(client.createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> {
                    assertFalse(inTransaction.get(), "Network request must not hold transaction locks");
                    assertNotNull(saved.get(), "Payment reservation must exist before the request");
                    return provider("PENDING", 0);
                });
        service = new PayosPaymentService(client, "http://localhost:8081/crave", factory);
    }

    @Test
    void reservesServerAmountAndCodeBeforeRemoteCallAndReusesLink() {
        var created = service.create(order.getId(), order.getCustomerId());
        var repeated = service.create(order.getId(), order.getCustomerId());

        assertEquals(CODE, created.orderCode());
        assertEquals(100000, created.amount());
        assertEquals("VND", created.currency());
        assertEquals("CR3456789", created.description());
        assertEquals(created, repeated);
        verify(em, times(1)).persist(any(PayosPaymentLink.class));
        verify(client, times(1)).createPaymentLink(CODE, 100000, "CR3456789",
                "http://localhost:8081/crave/orders/O123456789?payment=return",
                "http://localhost:8081/crave/orders/O123456789?payment=cancel");
        var locking = inOrder(em);
        locking.verify(em).find(CustomerOrder.class, order.getId(), LockModeType.PESSIMISTIC_WRITE);
        locking.verify(em).find(Payment.class, payment.getId(), LockModeType.PESSIMISTIC_WRITE);
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getPaidAt());
    }

    @Test
    void foreignOwnerCannotCreateOrReadLink() {
        AccountException createFailure = assertThrows(AccountException.class, () -> service.create(order.getId(), "OTHER"));
        AccountException readFailure = assertThrows(AccountException.class, () -> service.status(order.getId(), "OTHER"));
        assertEquals(403, createFailure.getStatusCode());
        assertEquals(403, readFailure.getStatusCode());
        verifyNoInteractions(client);
        assertNull(saved.get());
    }

    @Test
    void cashCancelledRefundedAndInvalidAmountsAreRejected() {
        payment.setPaymentMethod(PaymentMethod.CASH);
        assertEquals(400, assertThrows(AccountException.class, () -> service.create(order.getId(), order.getCustomerId())).getStatusCode());
        payment.setPaymentMethod(PaymentMethod.E_WALLET);
        order.setStatus(OrderStatus.CANCELLED);
        assertThrows(AccountException.class, () -> service.create(order.getId(), order.getCustomerId()));
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        payment.setStatus(PaymentStatus.REFUNDED);
        assertThrows(AccountException.class, () -> service.create(order.getId(), order.getCustomerId()));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("1"));
        assertThrows(AccountException.class, () -> service.create(order.getId(), order.getCustomerId()));
        payment.setAmount(new BigDecimal("100000.10"));
        order.setTotalAmount(payment.getAmount());
        assertThrows(AccountException.class, () -> service.create(order.getId(), order.getCustomerId()));
        verifyNoInteractions(client);
    }

    @Test
    void timeoutOrDuplicateRecoversSameCodeAndHostedCheckoutWithoutQr() {
        PayosException timeout = mock(PayosException.class);
        doThrow(timeout).when(client).createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString());
        ObjectNode recovered = provider("PENDING", 0);
        recovered.remove(List.of("checkoutUrl", "qrCode", "paymentLinkId", "description"));
        recovered.put("id", LINK_ID);
        when(client.getPaymentLink(CODE)).thenReturn(recovered);

        var result = service.create(order.getId(), order.getCustomerId());
        assertEquals(CODE, result.orderCode());
        assertEquals("https://pay.payos.vn/web/" + LINK_ID, result.checkoutUrl());
        assertNull(result.qrCode());
        assertNull(result.description());
        verify(em, times(1)).persist(any(PayosPaymentLink.class));
        verify(client).getPaymentLink(CODE);
    }

    @Test
    void qrReadDoesNotCreateRemoteOrLocalPaymentAndEnforcesOwnerAndStatus() {
        assertThrows(AccountException.class, () -> service.qrPayload(order.getId(), order.getCustomerId()));
        verifyNoInteractions(client);
        assertNull(saved.get());
        service.create(order.getId(), order.getCustomerId());
        clearInvocations(client);
        assertEquals("000201010212-test", service.qrPayload(order.getId(), order.getCustomerId()));
        assertEquals(403, assertThrows(AccountException.class, () -> service.qrPayload(order.getId(), "OTHER")).getStatusCode());
        order.setStatus(OrderStatus.CANCELLED);
        assertThrows(AccountException.class, () -> service.qrPayload(order.getId(), order.getCustomerId()));
        order.setStatus(OrderStatus.PENDING_CONFIRMATION);
        payment.setStatus(PaymentStatus.SUCCESS);
        assertThrows(AccountException.class, () -> service.qrPayload(order.getId(), order.getCustomerId()));
        verifyNoInteractions(client);
        verify(em, times(1)).persist(any(PayosPaymentLink.class));
    }

    @Test
    void providerTransferDescriptionIsDisplayedWhileRequestDescriptionStaysDurable() {
        doReturn(provider("PENDING", 0).put("description", "CR3456789 EXACT PROVIDER REFERENCE"))
                .when(client).createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString());
        var result = service.create(order.getId(), order.getCustomerId());
        assertEquals("CR3456789 EXACT PROVIDER REFERENCE", result.description());
        assertEquals("CR3456789", saved.get().getDescription());
    }

    @Test
    void failedRemoteCallRetainsReservationForRetryWithSameCode() {
        PayosException timeout = mock(PayosException.class);
        doThrow(timeout).when(client).createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString());
        when(client.getPaymentLink(CODE)).thenThrow(timeout);
        assertThrows(PayosException.class, () -> service.create(order.getId(), order.getCustomerId()));
        assertNotNull(saved.get());
        assertEquals("CREATING", saved.get().getStatus());
        doReturn(provider("PENDING", 0)).when(client).createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString());
        assertEquals(CODE, service.create(order.getId(), order.getCustomerId()).orderCode());
        verify(em, times(1)).persist(any(PayosPaymentLink.class));
    }

    @Test
    void signedPartialTransferSettlesOnlyAfterProviderConfirmsFullTotal() {
        service.create(order.getId(), order.getCustomerId());
        when(client.getPaymentLink(CODE)).thenReturn(provider("PROCESSING", 30000));
        service.webhook(webhook(30000));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getPaidAt());
        when(client.getPaymentLink(CODE)).thenReturn(provider("PAID", 100000));
        service.webhook(webhook(70000));
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertNotNull(payment.getPaidAt());
        assertEquals("PAYOS:BANK-TRANSFER-1", payment.getTransactionRef());
    }

    @Test
    void overpaymentIsRecordedWhenProviderConfirmsOriginalAmountWasCovered() {
        service.create(order.getId(), order.getCustomerId());
        when(client.getPaymentLink(CODE)).thenReturn(provider("PAID", 120000));
        service.webhook(webhook(120000));
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(new BigDecimal("100000"), payment.getAmount());
    }

    @Test
    void paidProviderResponseWithWrongAmountOrIdentityCannotCredit() {
        service.create(order.getId(), order.getCustomerId());
        ObjectNode mismatch = provider("PAID", 100000).put("amount", 1);
        when(client.getPaymentLink(CODE)).thenReturn(mismatch);
        assertThrows(AccountException.class, () -> service.webhook(webhook(100000)));
        mismatch.put("amount", 100000).put("paymentLinkId", "foreign-link");
        assertThrows(AccountException.class, () -> service.webhook(webhook(100000)));
        mismatch.put("paymentLinkId", LINK_ID).put("orderCode", CODE + 1);
        assertThrows(AccountException.class, () -> service.webhook(webhook(100000)));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getPaidAt());
    }

    @Test
    void paidStatusWithoutFullTotalOrWithRemainingAmountCannotCredit() {
        service.create(order.getId(), order.getCustomerId());
        ObjectNode incomplete = provider("PAID", 99999);
        when(client.getPaymentLink(CODE)).thenReturn(incomplete);
        assertThrows(AccountException.class, () -> service.webhook(webhook(100000)));
        incomplete.put("amountPaid", 100000).put("amountRemaining", 1);
        assertThrows(AccountException.class, () -> service.webhook(webhook(100000)));
        incomplete.put("amountRemaining", 0).remove("amountPaid");
        assertThrows(AccountException.class, () -> service.webhook(webhook(100000)));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void webhookReplayAndDelayedPendingNeverDowngradeSuccessfulOrRefundedPayment() {
        service.create(order.getId(), order.getCustomerId());
        when(client.getPaymentLink(CODE)).thenReturn(provider("PAID", 100000));
        service.webhook(webhook(100000));
        LocalDateTime paidAt = payment.getPaidAt();
        String reference = payment.getTransactionRef();
        service.webhook(webhook(100000));
        assertEquals(paidAt, payment.getPaidAt());
        assertEquals(reference, payment.getTransactionRef());
        when(client.getPaymentLink(CODE)).thenReturn(provider("PENDING", 0));
        service.webhook(webhook(100000));
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("PAID", saved.get().getStatus());
        payment.setStatus(PaymentStatus.REFUNDED);
        when(client.getPaymentLink(CODE)).thenReturn(provider("PAID", 100000));
        service.webhook(webhook(100000));
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(paidAt, payment.getPaidAt());
        assertEquals(reference, payment.getTransactionRef());
        assertEquals("REFUNDED", service.status(order.getId(), order.getCustomerId()).status());
    }

    @Test
    void paymentRefreshPreservesAdministrativeSettlementCommittedAfterOrderWasLoaded() {
        service.create(order.getId(), order.getCustomerId());
        when(client.getPaymentLink(CODE)).thenReturn(provider("PAID", 100000));
        LocalDateTime originalTime = LocalDateTime.of(2026, 10, 12, 9, 30);
        // The first-level cache still has PENDING from loading the order. Refresh
        // represents the newer row committed by an administrator before our lock.
        doAnswer(call -> {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setPaidAt(originalTime);
            payment.setTransactionRef("ADMINISTRATIVE-REFUND");
            return null;
        }).when(em).refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        service.webhook(webhook(100000));
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(originalTime, payment.getPaidAt());
        assertEquals("ADMINISTRATIVE-REFUND", payment.getTransactionRef());
        payment.setStatus(PaymentStatus.PENDING);
        doAnswer(call -> {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(originalTime);
            payment.setTransactionRef("ADMINISTRATIVE-SETTLEMENT");
            return null;
        }).when(em).refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        service.webhook(webhook(100000));
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(originalTime, payment.getPaidAt());
        assertEquals("ADMINISTRATIVE-SETTLEMENT", payment.getTransactionRef());
    }

    @Test
    void lateActualPaymentIsRecordedWithoutReopeningCancelledOrder() {
        service.create(order.getId(), order.getCustomerId());
        order.setStatus(OrderStatus.CANCELLED);
        payment.setStatus(PaymentStatus.FAILED);
        when(client.getPaymentLink(CODE)).thenReturn(provider("PAID", 100000));
        service.webhook(webhook(100000));
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void signedUnknownSampleIsIgnoredWithoutGatewayRequest() {
        service.webhook(webhook(1000).put("orderCode", CODE + 1));
        verifyNoInteractions(client);
    }

    @Test
    void invalidWebhookCodeCurrencyOrNonIntegerAmountIsRejected() {
        assertEquals(400, assertThrows(AccountException.class, () -> service.webhook(webhook(1000).put("code", "01"))).getStatusCode());
        assertThrows(AccountException.class, () -> service.webhook(webhook(1000).put("currency", "USD")));
        assertThrows(AccountException.class, () -> service.webhook(webhook(1000).put("amount", 1.5)));
        assertThrows(AccountException.class, () -> service.webhook(webhook(0)));
        verifyNoInteractions(client);
    }

    @Test
    void cancellationCallsGatewayAfterLocalCommitAndRemovesPayableQr() {
        service.create(order.getId(), order.getCustomerId());
        order.setStatus(OrderStatus.CANCELLED);
        payment.setStatus(PaymentStatus.FAILED);
        when(client.cancelPaymentLink(CODE, "Order cancelled")).thenAnswer(call -> {
            assertFalse(inTransaction.get());
            return provider("CANCELLED", 0);
        });
        service.cancelForOrder(order.getId());
        when(client.getPaymentLink(CODE)).thenReturn(provider("CANCELLED", 0));
        var cancelled = service.status(order.getId(), order.getCustomerId());
        assertEquals("CANCELLED", cancelled.status());
        assertNull(cancelled.checkoutUrl());
        assertNull(cancelled.qrCode());
        assertThrows(AccountException.class, () -> service.create(order.getId(), order.getCustomerId()));
        verify(client).cancelPaymentLink(CODE, "Order cancelled");
    }

    @Test
    void cancellationRaceWithPaidTransferRecordsPaymentInsteadOfLosingIt() {
        service.create(order.getId(), order.getCustomerId());
        order.setStatus(OrderStatus.CANCELLED);
        payment.setStatus(PaymentStatus.FAILED);
        when(client.cancelPaymentLink(CODE, "Order cancelled")).thenThrow(mock(PayosException.class));
        when(client.getPaymentLink(CODE)).thenReturn(provider("PAID", 100000));
        service.cancelForOrder(order.getId());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void providerCannotSupplyForeignHostedCheckoutUrl() {
        doReturn(provider("PENDING", 0).put("checkoutUrl", "https://example.com/pay"))
                .when(client).createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString());
        assertThrows(AccountException.class, () -> service.create(order.getId(), order.getCustomerId()));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    void disabledPaymentFailsWithoutDatabaseOrNetworkCalls() {
        EntityManagerFactory unusedFactory = mock(EntityManagerFactory.class);
        PayosPaymentService disabled = new PayosPaymentService(null, null, unusedFactory);
        assertEquals(503, assertThrows(AccountException.class, () -> disabled.create("ORDER", "OWNER")).getStatusCode());
        verifyNoInteractions(unusedFactory);
        verifyNoInteractions(client);
    }

    private ObjectNode provider(String status, long amountPaid) {
        ObjectNode data = json.createObjectNode().put("paymentLinkId", LINK_ID).put("orderCode", CODE)
                .put("amount", 100000).put("currency", "VND").put("status", status)
                .put("amountPaid", amountPaid).put("amountRemaining", Math.max(0, 100000 - amountPaid))
                .put("description", "CR3456789")
                .put("checkoutUrl", "https://pay.payos.vn/web/" + LINK_ID).put("qrCode", "000201010212-test")
                .put("accountNumber", "123456789").put("accountName", "CRAVE TEST").put("bin", "970422");
        data.putArray("transactions").addObject().put("reference", "BANK-TRANSFER-1");
        return data;
    }

    private ObjectNode webhook(long amount) {
        return json.createObjectNode().put("code", "00").put("currency", "VND")
                .put("orderCode", CODE).put("paymentLinkId", LINK_ID).put("amount", amount);
    }
}
