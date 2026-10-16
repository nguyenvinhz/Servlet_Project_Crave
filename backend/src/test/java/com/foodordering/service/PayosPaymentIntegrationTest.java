package com.foodordering.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodordering.config.DatabaseConfig;
import com.foodordering.model.Customer;
import com.foodordering.model.CustomerOrder;
import com.foodordering.model.Payment;
import com.foodordering.model.PayosPaymentLink;
import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentMethod;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.AccountException;
import com.foodordering.integration.PayosClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL transactions with a mocked gateway; never sends or receives a real transfer. */
class PayosPaymentIntegrationTest {
    private static EntityManagerFactory factory;
    private final ObjectMapper json = new ObjectMapper();
    private final String token = UUID.randomUUID().toString().replace("-", "");
    private final String customerId = "YC" + token.substring(0, 8);
    private final String orderId = "YO" + token.substring(0, 8);
    private final String paymentId = "YP" + token.substring(0, 10);
    private final String fixtureName = "payOS integration " + token;
    private PayosClient client;
    private PayosPaymentService service;

    @BeforeAll
    static void connect() {
        factory = DatabaseConfig.getEntityManagerFactory();
        DatabaseConfig.verifyConnection();
    }

    @AfterAll
    static void disconnect() { DatabaseConfig.close(); }

    @BeforeEach
    void fixtures() {
        client = mock(PayosClient.class);
        service = new PayosPaymentService(client, "http://localhost:8081/crave", factory);
        transaction(em -> {
            String phone = "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
            Customer customer = new Customer(customerId, fixtureName, "payos-it-" + token + "@example.com", phone, "integration-hash");
            em.persist(customer);
            CustomerOrder order = new CustomerOrder(orderId);
            order.setCustomer(customer);
            order.setReceiverName(fixtureName);
            order.setReceiverPhone(phone);
            order.setFulfillmentType(FulfillmentType.PICKUP);
            order.setSubtotal(new BigDecimal("100000"));
            order.setDeliveryFee(BigDecimal.ZERO);
            em.persist(order);
            Payment payment = new Payment();
            payment.setId(paymentId);
            payment.setOrder(order);
            payment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setAmount(new BigDecimal("100000"));
            em.persist(payment);
        });
        when(client.createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenAnswer(call -> provider(call.getArgument(0), "PENDING", 0));
    }

    @AfterEach
    void cleanOnlyOwnedFixtures() {
        transaction(em -> {
            em.createNativeQuery("DELETE FROM customer_order WHERE order_id = :id AND customer_id = :customer")
                    .setParameter("id", orderId).setParameter("customer", customerId).executeUpdate();
            // Payment -> payos_payment_link cascades, including when an existing test deletes a payment.
            em.createNativeQuery("DELETE FROM user_account WHERE user_id = :id AND full_name = :name")
                    .setParameter("id", customerId).setParameter("name", fixtureName).executeUpdate();
        });
    }

    @Test
    void concurrentCreatesReserveOneDurableCodeWithoutHoldingLocksDuringNetworkCalls() throws Exception {
        CountDownLatch remoteRequests = new CountDownLatch(2);
        List<Long> remoteCodes = new CopyOnWriteArrayList<>();
        when(client.createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            long code = call.getArgument(0);
            remoteCodes.add(code);
            remoteRequests.countDown();
            assertTrue(remoteRequests.await(10, TimeUnit.SECONDS), "Other request must reach gateway while first request waits");
            return provider(code, "PENDING", 0);
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PayosPaymentService.PaymentLinkBean> first = workers.submit(() -> service.create(orderId, customerId));
            Future<PayosPaymentService.PaymentLinkBean> second = workers.submit(() -> service.create(orderId, customerId));
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.orderCode(), b.orderCode());
            assertEquals(2, remoteCodes.size());
            assertEquals(remoteCodes.get(0), remoteCodes.get(1));
            try (EntityManager em = factory.createEntityManager()) {
                List<PayosPaymentLink> rows = em.createQuery("SELECT l FROM PayosPaymentLink l WHERE l.payment.id = :id", PayosPaymentLink.class)
                        .setParameter("id", paymentId).getResultList();
                assertEquals(1, rows.size());
                assertEquals(a.orderCode(), rows.get(0).getOrderCode());
                assertEquals(100000, rows.get(0).getAmount());
                assertEquals("PENDING", em.find(Payment.class, paymentId).getStatus().name());
            }
        } finally { workers.shutdownNow(); }
    }

    @Test
    void partialSettlementReplayAndRefundPreservePersistedAccounting() {
        long code = service.create(orderId, customerId).orderCode();
        when(client.getPaymentLink(code)).thenReturn(provider(code, "PROCESSING", 30000));
        service.webhook(webhook(code, 30000));
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(PaymentStatus.PENDING, em.find(Payment.class, paymentId).getStatus());
        }
        when(client.getPaymentLink(code)).thenReturn(provider(code, "PAID", 100000));
        service.webhook(webhook(code, 70000));
        java.time.LocalDateTime originalPaidAt;
        try (EntityManager em = factory.createEntityManager()) {
            Payment paid = em.find(Payment.class, paymentId);
            assertEquals(PaymentStatus.SUCCESS, paid.getStatus());
            originalPaidAt = paid.getPaidAt();
            assertNotNull(originalPaidAt);
        }
        service.webhook(webhook(code, 70000));
        transaction(em -> em.find(Payment.class, paymentId).setStatus(PaymentStatus.REFUNDED));
        service.webhook(webhook(code, 100000));
        try (EntityManager em = factory.createEntityManager()) {
            Payment refunded = em.find(Payment.class, paymentId);
            assertEquals(PaymentStatus.REFUNDED, refunded.getStatus());
            assertEquals(originalPaidAt, refunded.getPaidAt());
        }
    }

    @Test
    void orderCancellationDuringCreationCancelsGatewayLinkAfterReservation() {
        when(client.createPaymentLink(anyLong(), anyLong(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            transaction(em -> {
                em.find(CustomerOrder.class, orderId).setStatus(OrderStatus.CANCELLED);
                em.find(Payment.class, paymentId).setStatus(PaymentStatus.FAILED);
            });
            return provider(call.getArgument(0), "PENDING", 0);
        });
        when(client.cancelPaymentLink(anyLong(), anyString())).thenAnswer(call -> provider(call.getArgument(0), "CANCELLED", 0));
        assertEquals(409, assertThrows(AccountException.class, () -> service.create(orderId, customerId)).getStatusCode());
        verify(client).cancelPaymentLink(anyLong(), eq("Order cancelled"));
        try (EntityManager em = factory.createEntityManager()) {
            PayosPaymentLink link = em.createQuery("SELECT l FROM PayosPaymentLink l WHERE l.payment.id = :id", PayosPaymentLink.class)
                    .setParameter("id", paymentId).getSingleResult();
            assertEquals("CANCELLED", link.getStatus());
            assertEquals(OrderStatus.CANCELLED, em.find(CustomerOrder.class, orderId).getStatus());
            assertEquals(PaymentStatus.FAILED, em.find(Payment.class, paymentId).getStatus());
        }
    }

    private ObjectNode provider(long code, String status, long amountPaid) {
        return json.createObjectNode().put("paymentLinkId", "test-link-" + code).put("orderCode", code)
                .put("amount", 100000).put("currency", "VND").put("status", status)
                .put("amountPaid", amountPaid).put("amountRemaining", Math.max(0, 100000 - amountPaid))
                .put("checkoutUrl", "https://pay.payos.vn/web/test-link-" + code).put("qrCode", "000201010212-test");
    }

    private ObjectNode webhook(long code, long amount) {
        return json.createObjectNode().put("code", "00").put("currency", "VND").put("orderCode", code)
                .put("paymentLinkId", "test-link-" + code).put("amount", amount);
    }

    private void transaction(Consumer<EntityManager> work) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try {
                work.accept(em);
                em.getTransaction().commit();
            } catch (RuntimeException | Error failure) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw failure;
            }
        }
    }
}
