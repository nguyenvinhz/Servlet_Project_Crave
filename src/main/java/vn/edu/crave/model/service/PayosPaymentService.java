package vn.edu.crave.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.crave.config.DatabaseConfig;
import vn.edu.crave.config.PayosConfig;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Function;

/** payOS settlement is confirmed by the gateway, never by a browser return URL. */
public class PayosPaymentService {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final Set<String> GATEWAY_STATUSES = Set.of("PENDING", "PROCESSING", "PAID", "CANCELLED", "EXPIRED");
    private final PayosClient client;
    private final String publicBaseUrl;
    private final EntityManagerFactory factory;

    public PayosPaymentService() { this(PayosConfig.fromEnvironment()); }

    private PayosPaymentService(PayosConfig config) {
        this(config.configured() ? config.client() : null, config.publicBaseUrl(), null);
    }

    public PayosPaymentService(PayosClient client, String publicBaseUrl, EntityManagerFactory factory) {
        this.client = client;
        this.publicBaseUrl = publicBaseUrl == null ? null : publicBaseUrl.replaceAll("/+$", "");
        this.factory = factory;
    }

    public record PaymentLinkResponse(String orderId, String paymentId, long orderCode, long amount,
            String currency, String status, String paymentStatus, String checkoutUrl, String qrCode,
            String accountNumber, String accountName, String description, String bin) { }

    public PaymentLinkResponse create(String orderId, String customerId) {
        requireConfigured();
        Snapshot reservation = transaction(em -> {
            CustomerOrder order = ownedOrder(em, orderId, customerId, true);
            Payment payment = lockedPayment(em, order);
            requireOnline(payment);
            if (order.getStatus() == OrderStatus.CANCELLED || payment.getStatus() == PaymentStatus.REFUNDED) {
                throw problem("PAYMENT_NOT_ALLOWED", "Đơn hàng đã hủy hoặc thanh toán đã hoàn tiền.", 409);
            }
            PayosPaymentLink link = findByPayment(em, payment.getId());
            if (link == null) {
                if (payment.getStatus() == PaymentStatus.SUCCESS) {
                    throw problem("ALREADY_PAID", "Đơn hàng đã được thanh toán.", 409);
                }
                long amount = payableAmount(order, payment);
                link = new PayosPaymentLink();
                link.setPayment(payment);
                link.setAmount(amount);
                String suffix = order.getId().replaceAll("[^A-Za-z0-9]", "");
                link.setDescription("CR" + suffix.substring(Math.max(0, suffix.length() - 7)));
                em.persist(link);
                em.flush(); // Allocate and persist the identity before any gateway request.
                if (link.getOrderCode() == null || link.getOrderCode() <= 0 || link.getOrderCode() > MAX_SAFE_INTEGER) {
                    throw problem("PAYMENT_CODE_EXHAUSTED", "Không thể tạo mã thanh toán. Vui lòng liên hệ cửa hàng.", 503);
                }
            } else if (Set.of("CANCELLED", "EXPIRED").contains(link.getStatus())) {
                throw problem("PAYMENT_LINK_CLOSED", "Đường dẫn thanh toán đã hết hạn hoặc đã hủy.", 409);
            }
            return snapshot(order, payment, link);
        });

        if (reservation.paymentStatus == PaymentStatus.SUCCESS || reservation.checkoutUrl != null) {
            return response(reservation);
        }
        String orderPath = publicBaseUrl + "/orders/" + pathSegment(reservation.orderId);
        JsonNode data;
        boolean authoritative = false;
        try {
            data = client.createPaymentLink(reservation.orderCode, reservation.amount, reservation.description,
                    orderPath + "?payment=return", orderPath + "?payment=cancel");
        } catch (PayosException creationFailure) {
            // A timeout or concurrent duplicate can occur after the gateway created the link.
            // Reuse the durable order code; never allocate another payable link on retry.
            try {
                data = client.getPaymentLink(reservation.orderCode);
                authoritative = true;
            } catch (PayosException retrievalFailure) {
                throw creationFailure;
            }
        }
        if ("PAID".equals(data.path("status").asText()) && !authoritative) {
            data = client.getPaymentLink(reservation.orderCode);
            authoritative = true;
        }
        Snapshot updated = reconcile(reservation.orderId, reservation.orderCode, data, authoritative);
        if (updated.orderStatus == OrderStatus.CANCELLED && updated.paymentStatus != PaymentStatus.SUCCESS
                && updated.paymentStatus != PaymentStatus.REFUNDED) {
            cancelForOrder(updated.orderId);
            throw problem("PAYMENT_NOT_ALLOWED", "Đơn hàng đã bị hủy trong lúc tạo thanh toán.", 409);
        }
        return response(updated);
    }

    public PaymentLinkResponse status(String orderId, String customerId) {
        requireConfigured();
        Snapshot stored = transaction(em -> {
            CustomerOrder order = ownedOrder(em, orderId, customerId, false);
            Payment payment = order.getPayment();
            requireOnline(payment);
            PayosPaymentLink link = findByPayment(em, payment.getId());
            if (link == null) throw problem("PAYMENT_LINK_NOT_FOUND", "Chưa có đường dẫn thanh toán cho đơn hàng này.", 404);
            return snapshot(order, payment, link);
        });
        if (stored.paymentStatus == PaymentStatus.SUCCESS || stored.paymentStatus == PaymentStatus.REFUNDED) {
            return response(stored);
        }
        JsonNode data = client.getPaymentLink(stored.orderCode);
        Snapshot updated = reconcile(stored.orderId, stored.orderCode, data, true);
        if (updated.orderStatus == OrderStatus.CANCELLED && updated.paymentStatus != PaymentStatus.SUCCESS
                && updated.paymentStatus != PaymentStatus.REFUNDED && !"CANCELLED".equals(updated.status)
                && !"EXPIRED".equals(updated.status)) {
            cancelForOrder(updated.orderId);
            return response(transaction(em -> loadByCode(em, updated.orderCode)));
        }
        return response(updated);
    }

    public String qrPayload(String orderId, String customerId) {
        requireConfigured();
        return transaction(em -> {
            CustomerOrder order = ownedOrder(em, orderId, customerId, false);
            Payment payment = order.getPayment();
            requireOnline(payment);
            PayosPaymentLink link = findByPayment(em, payment.getId());
            if (order.getStatus() == OrderStatus.CANCELLED || payment.getStatus() == PaymentStatus.SUCCESS
                    || payment.getStatus() == PaymentStatus.REFUNDED || link == null
                    || !Set.of("PENDING", "PROCESSING").contains(link.getStatus()) || link.getQrCode() == null) {
                throw problem("PAYMENT_QR_UNAVAILABLE", "Mã QR chưa sẵn sàng. Vui lòng mở đường dẫn thanh toán.", 409);
            }
            return link.getQrCode();
        });
    }

    /** The caller must pass data returned by PayosClient.verifyWebhook, never an unsigned body. */
    public void webhook(JsonNode verifiedData) {
        requireConfigured();
        if (verifiedData == null || !"00".equals(verifiedData.path("code").asText())
                || !"VND".equals(verifiedData.path("currency").asText())) {
            throw problem("INVALID_WEBHOOK", "Dữ liệu thông báo thanh toán không hợp lệ.", 400);
        }
        long orderCode = positiveInteger(verifiedData.get("orderCode"), "INVALID_WEBHOOK", 400);
        positiveInteger(verifiedData.get("amount"), "INVALID_WEBHOOK", 400);
        Snapshot stored = transaction(em -> loadByCode(em, orderCode));
        // payOS confirms a webhook URL with a signed sample unrelated to a real order.
        if (stored == null) return;
        String eventLinkId = linkId(verifiedData);
        if (stored.paymentLinkId != null && !stored.paymentLinkId.equals(eventLinkId)) {
            throw problem("INVALID_WEBHOOK", "Thông báo thanh toán không khớp đơn hàng.", 400);
        }
        JsonNode data = client.getPaymentLink(orderCode);
        if (!eventLinkId.equals(linkId(data))) {
            throw problem("INVALID_WEBHOOK", "Thông báo thanh toán không khớp đường dẫn thanh toán.", 400);
        }
        // A webhook amount is a single transfer (possibly partial/overpaid). Only the
        // gateway's original amount and confirmed total determine whether the order is paid.
        reconcile(stored.orderId, orderCode, data, true);
    }

    /** Call after the local order-cancellation transaction commits. No network call holds a DB lock. */
    public void cancelForOrder(String orderId) {
        if (client == null) return;
        Snapshot stored = transaction(em -> {
            CustomerOrder order = em.find(CustomerOrder.class, orderId);
            if (order == null || order.getStatus() != OrderStatus.CANCELLED || order.getPayment() == null) return null;
            PayosPaymentLink link = findByPayment(em, order.getPayment().getId());
            return link == null ? null : snapshot(order, order.getPayment(), link);
        });
        if (stored == null || stored.paymentStatus == PaymentStatus.SUCCESS || stored.paymentStatus == PaymentStatus.REFUNDED
                || Set.of("CANCELLED", "EXPIRED").contains(stored.status)) return;
        JsonNode data;
        try {
            data = client.cancelPaymentLink(stored.orderCode, "Order cancelled");
        } catch (PayosException cancellationFailure) {
            // A paid link cannot be cancelled; reconcile the real payment instead of losing it.
            try { data = client.getPaymentLink(stored.orderCode); }
            catch (PayosException retrievalFailure) { throw cancellationFailure; }
        }
        // Always query payment totals when a cancellation races a successful transfer.
        if ("PAID".equals(data.path("status").asText())) data = client.getPaymentLink(stored.orderCode);
        reconcile(stored.orderId, stored.orderCode, data, true);
    }

    private Snapshot reconcile(String orderId, long orderCode, JsonNode data, boolean authoritative) {
        return transaction(em -> {
            CustomerOrder order = em.find(CustomerOrder.class, orderId, LockModeType.PESSIMISTIC_WRITE);
            if (order == null) throw problem("ORDER_NOT_FOUND", "Đơn hàng không tồn tại.", 404);
            Payment payment = lockedPayment(em, order);
            PayosPaymentLink link = em.find(PayosPaymentLink.class, orderCode);
            if (link == null || !payment.getId().equals(link.getPayment().getId())) {
                throw problem("PAYMENT_LINK_NOT_FOUND", "Không tìm thấy đường dẫn thanh toán.", 404);
            }
            String id = linkId(data);
            if (positiveInteger(data.get("orderCode"), "PAYOS_DATA_MISMATCH", 502) != orderCode
                    || positiveInteger(data.get("amount"), "PAYOS_DATA_MISMATCH", 502) != link.getAmount()
                    || (data.has("currency") && !"VND".equals(data.path("currency").asText()))
                    || (link.getPaymentLinkId() != null && !link.getPaymentLinkId().equals(id))
                    || payment.getAmount() == null || payment.getAmount().compareTo(java.math.BigDecimal.valueOf(link.getAmount())) != 0) {
                throw problem("PAYOS_DATA_MISMATCH", "Dữ liệu cổng thanh toán không khớp đơn hàng.", 502);
            }
            String gatewayStatus = data.path("status").asText();
            if (!GATEWAY_STATUSES.contains(gatewayStatus)) {
                throw problem("PAYOS_DATA_MISMATCH", "Trạng thái cổng thanh toán không hợp lệ.", 502);
            }
            boolean paid = "PAID".equals(gatewayStatus);
            if (paid && (!authoritative || nonnegativeInteger(data.get("amountPaid")) < link.getAmount()
                    || nonnegativeInteger(data.get("amountRemaining")) != 0)) {
                throw problem("PAYOS_DATA_MISMATCH", "Cổng thanh toán chưa xác nhận đủ số tiền.", 502);
            }
            link.setPaymentLinkId(id);
            if (!Set.of("PAID", "CANCELLED", "EXPIRED").contains(link.getStatus()) || paid) link.setStatus(gatewayStatus);
            String checkoutUrl = optionalText(data, "checkoutUrl", 1024);
            if (checkoutUrl != null) {
                URI uri;
                try { uri = URI.create(checkoutUrl); }
                catch (IllegalArgumentException invalid) { throw problem("PAYOS_DATA_MISMATCH", "Đường dẫn thanh toán không hợp lệ.", 502); }
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !"pay.payos.vn".equalsIgnoreCase(uri.getHost())
                        || uri.getUserInfo() != null) {
                    throw problem("PAYOS_DATA_MISMATCH", "Đường dẫn thanh toán không hợp lệ.", 502);
                }
                link.setCheckoutUrl(checkoutUrl);
            } else if (link.getCheckoutUrl() == null) {
                link.setCheckoutUrl("https://pay.payos.vn/web/" + id);
            }
            String qr = optionalText(data, "qrCode", 8000);
            if (qr != null) link.setQrCode(qr);
            String accountNumber = optionalText(data, "accountNumber", 50);
            if (accountNumber != null) link.setAccountNumber(accountNumber);
            String accountName = optionalText(data, "accountName", 150);
            if (accountName != null) link.setAccountName(accountName);
            String bin = optionalText(data, "bin", 20);
            if (bin != null) link.setBin(bin);
            String transferDescription = optionalText(data, "description", 255);
            if (transferDescription != null) link.setTransferDescription(transferDescription);
            if (paid && payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.REFUNDED) {
                // A genuine late transfer still exists financially even after local cancellation.
                // Record it for reconciliation/refund, while leaving the cancelled order untouched.
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(LocalDateTime.now());
                payment.setTransactionRef(transactionReference(data, id));
            } else if (Set.of("CANCELLED", "EXPIRED").contains(gatewayStatus) && payment.getStatus() == PaymentStatus.PENDING) {
                payment.setStatus(PaymentStatus.FAILED);
            }
            return snapshot(order, payment, link);
        });
    }

    private CustomerOrder ownedOrder(EntityManager em, String orderId, String customerId, boolean lock) {
        if (orderId == null || orderId.isBlank() || customerId == null || customerId.isBlank()) {
            throw problem("INVALID_REQUEST", "Mã đơn hàng hoặc tài khoản không hợp lệ.", 400);
        }
        CustomerOrder order = lock ? em.find(CustomerOrder.class, orderId, LockModeType.PESSIMISTIC_WRITE)
                : em.find(CustomerOrder.class, orderId);
        if (order == null) throw problem("ORDER_NOT_FOUND", "Đơn hàng không tồn tại.", 404);
        if (!customerId.equals(order.getCustomerId())) throw problem("FORBIDDEN", "Bạn không có quyền truy cập đơn hàng này.", 403);
        return order;
    }

    private Payment lockedPayment(EntityManager em, CustomerOrder order) {
        if (order.getPayment() == null) throw problem("PAYMENT_NOT_FOUND", "Thanh toán không tồn tại.", 404);
        Payment payment = em.find(Payment.class, order.getPayment().getId(), LockModeType.PESSIMISTIC_WRITE);
        if (payment == null) throw problem("PAYMENT_NOT_FOUND", "Thanh toán không tồn tại.", 404);
        // CustomerOrder.payment may already be cached from the initial order load.
        // Re-read after locking so an administrator's concurrent SUCCESS/REFUNDED
        // update cannot be overwritten using that earlier PENDING snapshot.
        em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);
        return payment;
    }

    private static void requireOnline(Payment payment) {
        if (payment == null) throw problem("PAYMENT_NOT_FOUND", "Thanh toán không tồn tại.", 404);
        if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER && payment.getPaymentMethod() != PaymentMethod.E_WALLET) {
            throw problem("PAYMENT_METHOD_UNSUPPORTED", "Mã QR chỉ áp dụng cho chuyển khoản hoặc ví điện tử.", 400);
        }
    }

    private static long payableAmount(CustomerOrder order, Payment payment) {
        try {
            long amount = payment.getAmount().longValueExact();
            if (amount <= 0 || amount > MAX_SAFE_INTEGER || payment.getAmount().compareTo(order.getTotalAmount()) != 0) {
                throw new ArithmeticException();
            }
            return amount;
        } catch (ArithmeticException | NullPointerException invalid) {
            throw problem("INVALID_PAYMENT_AMOUNT", "Số tiền thanh toán không hợp lệ.", 409);
        }
    }

    private PayosPaymentLink findByPayment(EntityManager em, String paymentId) {
        return em.createQuery("SELECT l FROM PayosPaymentLink l WHERE l.payment.id = :paymentId", PayosPaymentLink.class)
                .setParameter("paymentId", paymentId).getResultList().stream().findFirst().orElse(null);
    }

    private Snapshot loadByCode(EntityManager em, long orderCode) {
        PayosPaymentLink link = em.find(PayosPaymentLink.class, orderCode);
        return link == null ? null : snapshot(link.getPayment().getOrder(), link.getPayment(), link);
    }

    private record Snapshot(String orderId, String paymentId, long orderCode, long amount, String description, String transferDescription,
            String paymentLinkId, String status, String checkoutUrl, String qrCode, String accountNumber,
            String accountName, String bin, OrderStatus orderStatus, PaymentStatus paymentStatus) { }

    private static Snapshot snapshot(CustomerOrder order, Payment payment, PayosPaymentLink link) {
        return new Snapshot(order.getId(), payment.getId(), link.getOrderCode(), link.getAmount(), link.getDescription(), link.getTransferDescription(),
                link.getPaymentLinkId(), link.getStatus(), link.getCheckoutUrl(), link.getQrCode(), link.getAccountNumber(),
                link.getAccountName(), link.getBin(), order.getStatus(), payment.getStatus());
    }

    private static PaymentLinkResponse response(Snapshot stored) {
        String status = stored.paymentStatus == PaymentStatus.REFUNDED ? "REFUNDED"
                : stored.paymentStatus == PaymentStatus.SUCCESS ? "PAID"
                : stored.orderStatus == OrderStatus.CANCELLED ? "CANCELLED" : stored.status;
        boolean payable = Set.of("PENDING", "PROCESSING").contains(status);
        return new PaymentLinkResponse(stored.orderId, stored.paymentId, stored.orderCode, stored.amount, "VND", status,
                stored.paymentStatus.name(), payable ? stored.checkoutUrl : null, payable ? stored.qrCode : null,
                stored.accountNumber, stored.accountName, stored.transferDescription, stored.bin);
    }

    private static String linkId(JsonNode data) {
        String id = data == null ? "" : data.path("paymentLinkId").asText(data.path("id").asText());
        if (!id.matches("[A-Za-z0-9_-]{1,64}")) throw problem("PAYOS_DATA_MISMATCH", "Mã đường dẫn thanh toán không hợp lệ.", 502);
        return id;
    }

    private static String optionalText(JsonNode data, String field, int maxLength) {
        JsonNode value = data.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.asText().length() > maxLength) {
            throw problem("PAYOS_DATA_MISMATCH", "Dữ liệu cổng thanh toán không hợp lệ.", 502);
        }
        return value.asText().isBlank() ? null : value.asText();
    }

    private static String transactionReference(JsonNode data, String linkId) {
        for (JsonNode transfer : data.path("transactions")) {
            JsonNode reference = transfer.get("reference");
            if (reference != null && reference.isTextual() && reference.asText().matches("[A-Za-z0-9_.:-]{1,94}")) {
                return "PAYOS:" + reference.asText();
            }
        }
        return "PAYOS:" + linkId;
    }

    private static long positiveInteger(JsonNode node, String code, int status) {
        long value = integer(node, code, status);
        if (value <= 0) throw problem(code, "Số tiền hoặc mã thanh toán không hợp lệ.", status);
        return value;
    }

    private static long nonnegativeInteger(JsonNode node) { return integer(node, "PAYOS_DATA_MISMATCH", 502); }

    private static long integer(JsonNode node, String code, int status) {
        try {
            if (node == null || !(node.isIntegralNumber() || (node.isTextual() && node.asText().matches("[0-9]+")))) {
                throw new NumberFormatException();
            }
            long value = Long.parseLong(node.asText());
            if (value < 0 || value > MAX_SAFE_INTEGER) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw problem(code, "Số tiền hoặc mã thanh toán không hợp lệ.", status);
        }
    }

    private void requireConfigured() {
        if (client == null || publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw problem("PAYOS_NOT_CONFIGURED", "Thanh toán trực tuyến chưa được cấu hình.", 503);
        }
    }

    private <T> T transaction(Function<EntityManager, T> work) {
        EntityManagerFactory emf = factory != null ? factory : DatabaseConfig.getEntityManagerFactory();
        try (EntityManager em = emf.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                T result = work.apply(em);
                tx.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                if (tx.isActive()) tx.rollback();
                throw failure;
            }
        }
    }

    private static String pathSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static AccountException problem(String code, String message, int status) {
        return new AccountException(code, message, status);
    }
}
