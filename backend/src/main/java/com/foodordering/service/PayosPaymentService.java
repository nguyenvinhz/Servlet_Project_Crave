package com.foodordering.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.config.DatabaseConfig;
import com.foodordering.config.PayosConfig;
import com.foodordering.entity.CustomerOrder;
import com.foodordering.entity.Payment;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentMethod;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.AccountException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.PaymentGatewayException;
import com.foodordering.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.time.LocalDateTime;

public class PayosPaymentService {
    public record CheckoutLink(String orderId, String checkoutUrl) { }
    public record PaymentResult(String status) { }
    private record Reservation(long code, long amount, boolean retry) { }
    private record Reconciliation(Long code, String status) { }
    private final EntityManagerFactory emf;
    private final PayosGateway gateway;
    private static final SecureRandom RANDOM = new SecureRandom();

    public PayosPaymentService() { this(null, new HttpPayosGateway(PayosConfig.fromEnvironment())); }
    public PayosPaymentService(EntityManagerFactory emf, PayosGateway gateway) { this.emf = emf; this.gateway = gateway; }
    private EntityManager entityManager() { return (emf == null ? DatabaseConfig.getEntityManagerFactory() : emf).createEntityManager(); }

    public CheckoutLink createLink(String customerId, String orderId) {
        return createLink(customerId, orderId, true);
    }

    private CheckoutLink createLink(String customerId, String orderId, boolean allowRenewal) {
        gateway.requireConfigured();
        // Reserve the stable gateway code before HTTP, so retries after an interrupted response cannot create a second charge.
        Reservation reservation = transaction(em -> {
            CustomerOrder order = ownedOrder(em, customerId, orderId);
            Payment payment = bankPayment(order);
            em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
            requirePayable(order, payment);
            boolean retry = payment.getPayosOrderCode() != null;
            if (!retry) payment.setPayosOrderCode(1 + (RANDOM.nextLong() & 0x0000ffffffffffffL));
            return new Reservation(payment.getPayosOrderCode(), exactAmount(payment), retry);
        });
        PayosGateway.PaymentLink link = gateway.createOrGet(reservation.code(), reservation.amount(), orderId, reservation.retry());
        if (link.orderCode() != reservation.code() || link.amount() != reservation.amount()) throw mismatch();
        if ("PAID".equals(link.status())) {
            syncPayment(customerId, orderId);
            throw new BadRequestException("Đơn hàng này đã được thanh toán. Vui lòng tải lại chi tiết đơn hàng.");
        }
        if (allowRenewal && ("CANCELLED".equals(link.status()) || "EXPIRED".equals(link.status()))) {
            PayosGateway.PaymentState closed = link.amountPaid() == null ? gateway.getPayment(reservation.code())
                    : new PayosGateway.PaymentState(link.orderCode(), link.amount(), link.amountPaid(), link.paymentLinkId(), link.status(), "");
            if (!("CANCELLED".equals(closed.status()) || "EXPIRED".equals(closed.status())) || closed.amountPaid() != 0) {
                throw new BadRequestException("Liên kết thanh toán cần được cửa hàng đối soát trước khi thử lại.");
            }
            transaction(em -> {
                CustomerOrder order = ownedOrder(em, customerId, orderId);
                Payment payment = bankPayment(order);
                em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
                requirePayable(order, payment);
                if (payment.getPayosOrderCode() == reservation.code()) {
                    validateRemote(payment, closed.orderCode(), closed.amount(), closed.paymentLinkId());
                    if (!closed.paymentLinkId().equals(link.paymentLinkId())) throw mismatch();
                    payment.setPayosOrderCode(null);
                    payment.setPayosPaymentLinkId(null);
                    payment.setPayosCheckoutUrl(null);
                }
                return null;
            });
            return createLink(customerId, orderId, false);
        }
        if (!"PENDING".equals(link.status())) throw new BadRequestException("Liên kết thanh toán đã đóng. Vui lòng liên hệ cửa hàng để kiểm tra đơn hàng.");
        return transaction(em -> {
            CustomerOrder order = ownedOrder(em, customerId, orderId);
            Payment payment = bankPayment(order);
            em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
            requirePayable(order, payment);
            validateRemote(payment, link.orderCode(), link.amount(), link.paymentLinkId());
            payment.setPayosPaymentLinkId(link.paymentLinkId());
            payment.setPayosCheckoutUrl(link.checkoutUrl());
            return new CheckoutLink(orderId, link.checkoutUrl());
        });
    }

    /** Only the gateway's authenticated API can reconcile a browser return, never the URL's status flags. */
    public PaymentResult syncPayment(String customerId, String orderId) {
        Reconciliation reconciliation = transaction(em -> {
            CustomerOrder order = ownedOrder(em, customerId, orderId);
            Payment payment = bankPayment(order);
            if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED
                    || payment.getPayosOrderCode() == null) return new Reconciliation(null, payment.getStatus().name());
            return new Reconciliation(payment.getPayosOrderCode(), payment.getStatus().name());
        });
        if (reconciliation.code() == null) return new PaymentResult(reconciliation.status());
        PayosGateway.PaymentState state = gateway.getPayment(reconciliation.code());
        return transaction(em -> {
            CustomerOrder order = ownedOrder(em, customerId, orderId);
            Payment payment = bankPayment(order);
            em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
            validateRemote(payment, state.orderCode(), state.amount(), state.paymentLinkId());
            if ("PAID".equals(state.status()) && state.amountPaid() >= exactAmount(payment)) {
                markPaid(payment, state.reference().isBlank() ? "PAYOS:" + state.paymentLinkId() : state.reference());
            }
            return new PaymentResult(payment.getStatus().name());
        });
    }

    public void receiveWebhook(JsonNode payload) {
        PayosGateway.WebhookPayment received = gateway.verifyWebhook(payload);
        if (!received.successful()) return;
        transaction(em -> {
            var matches = em.createQuery("SELECT p FROM Payment p WHERE p.payosOrderCode = :code", Payment.class)
                    .setParameter("code", received.orderCode()).getResultList();
            // payOS sends a signed sample when registering the callback; unknown samples have no local payment to mutate.
            if (matches.isEmpty()) return null;
            Payment payment = matches.get(0);
            em.lock(payment.getOrder(), LockModeType.PESSIMISTIC_WRITE);
            em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
            validateLink(payment, received.orderCode(), received.paymentLinkId());
            if (received.amount() <= 0) throw mismatch();
            if (received.amount() != exactAmount(payment)) {
                // A webhook reports one bank transaction, while the gateway may aggregate several partial transfers.
                PayosGateway.PaymentState remote = gateway.getPayment(received.orderCode());
                if (remote == null) throw mismatch();
                validateRemote(payment, remote.orderCode(), remote.amount(), remote.paymentLinkId());
                if (!received.paymentLinkId().equals(remote.paymentLinkId()) || remote.amountPaid() < received.amount()) throw mismatch();
                if ("PAID".equals(remote.status()) && remote.amountPaid() >= exactAmount(payment)) {
                    markPaid(payment, received.reference());
                }
                return null;
            }
            // A callback can beat the create-link response. Confirm its link ID through the authenticated API before saving it.
            if (payment.getPayosPaymentLinkId() == null) {
                PayosGateway.PaymentState remote = gateway.getPayment(received.orderCode());
                validateRemote(payment, remote.orderCode(), remote.amount(), remote.paymentLinkId());
                if (!received.paymentLinkId().equals(remote.paymentLinkId()) || !"PAID".equals(remote.status())
                        || remote.amountPaid() < exactAmount(payment)) throw mismatch();
            }
            markPaid(payment, received.reference());
            return null;
        });
    }

    private static CustomerOrder ownedOrder(EntityManager em, String customerId, String orderId) {
        if (orderId == null || !orderId.matches("[A-Za-z0-9]{1,10}")) throw new BadRequestException("Mã đơn hàng không hợp lệ.");
        CustomerOrder order = em.find(CustomerOrder.class, orderId);
        if (order == null) throw new ResourceNotFoundException("Đơn hàng không tồn tại.");
        if (!customerId.equals(order.getCustomerId())) throw new AccountException("FORBIDDEN", "Bạn không có quyền thanh toán đơn hàng này.", 403);
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }
    private static Payment bankPayment(CustomerOrder order) {
        Payment payment = order.getPayment();
        if (payment == null || payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) throw new BadRequestException("Đơn hàng này không sử dụng chuyển khoản PayOS.");
        return payment;
    }
    private static void requirePayable(CustomerOrder order, Payment payment) {
        if (order.getStatus() == OrderStatus.CANCELLED) throw new BadRequestException("Không thể thanh toán đơn hàng đã hủy.");
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) throw new BadRequestException("Đơn hàng này đã được thanh toán.");
        exactAmount(payment);
    }
    private static long exactAmount(Payment payment) {
        try {
            long amount = payment.getAmount().longValueExact();
            if (amount <= 0) throw new ArithmeticException();
            return amount;
        } catch (ArithmeticException | NullPointerException invalid) { throw new BadRequestException("Số tiền thanh toán không hợp lệ."); }
    }
    private static void validateRemote(Payment payment, long code, long amount, String linkId) {
        validateLink(payment, code, linkId);
        if (exactAmount(payment) != amount) throw mismatch();
    }
    private static void validateLink(Payment payment, long code, String linkId) {
        if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER || payment.getPayosOrderCode() == null
                || payment.getPayosOrderCode() != code || linkId == null || linkId.isBlank()
                || linkId.length() > 100 || (payment.getPayosPaymentLinkId() != null && !payment.getPayosPaymentLinkId().equals(linkId))) throw mismatch();
    }
    private static void markPaid(Payment payment, String reference) {
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) return;
        if (payment.getOrder().getStatus() == OrderStatus.CANCELLED) throw new BadRequestException("Không thể xác nhận thanh toán cho đơn hàng đã hủy. Vui lòng đối soát với cửa hàng.");
        if (reference == null || reference.isBlank() || reference.length() > 100) throw mismatch();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionRef(reference);
    }
    private static PaymentGatewayException mismatch() { return new PaymentGatewayException("PAYOS_PAYMENT_MISMATCH", "Thông tin thanh toán không khớp với đơn hàng.", 400); }
    private <T> T transaction(java.util.function.Function<EntityManager, T> operation) {
        try (EntityManager em = entityManager()) {
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                T result = operation.apply(em);
                tx.commit();
                return result;
            } catch (RuntimeException exception) {
                if (tx.isActive()) tx.rollback();
                throw exception;
            }
        }
    }
}
