package com.foodordering.service;

import com.foodordering.entity.Payment;
import com.foodordering.entity.CustomerOrder;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.dao.PaymentDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;

class PaymentServiceTest {

    private PaymentDAO paymentRepository;
    private EntityManagerFactory emf;
    private EntityManager em;
    private EntityTransaction tx;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentDAO.class);
        emf = mock(EntityManagerFactory.class);
        em = mock(EntityManager.class);
        tx = mock(EntityTransaction.class);

        when(emf.createEntityManager()).thenReturn(em);
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);

        paymentService = new PaymentService(paymentRepository, emf);
    }

    @Test
    @DisplayName("Cập nhật trạng thái thanh toán ném ResourceNotFoundException khi paymentId không tồn tại")
    void updatePaymentStatusThrowsNotFoundWhenPaymentDoesNotExist() {
        when(em.find(Payment.class, "UNKNOWN_PAYMENT")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.updatePaymentStatus("UNKNOWN_PAYMENT", PaymentStatus.SUCCESS, "TXN_123"));
        verify(em).close();
    }

    @Test
    @DisplayName("Cập nhật trạng thái sang SUCCESS thành công và lưu mã giao dịch")
    void updatePaymentStatusToSuccessSucceeds() {
        Payment payment = new Payment();
        payment.setId("TT01");
        payment.setStatus(PaymentStatus.PENDING);

        when(em.find(Payment.class, "TT01")).thenReturn(payment);

        paymentService.updatePaymentStatus("TT01", PaymentStatus.SUCCESS, "TXN_VN_PAY_123");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("TXN_VN_PAY_123", payment.getTransactionRef());
        assertNotNull(payment.getPaidAt());
        verify(em).merge(payment);
        verify(tx).commit();
        verify(em).close();
    }

    @Test
    @DisplayName("Cập nhật trạng thái sang FAILED thành công")
    void updatePaymentStatusToFailedSucceeds() {
        Payment payment = new Payment();
        payment.setId("TT01");
        payment.setStatus(PaymentStatus.PENDING);

        when(em.find(Payment.class, "TT01")).thenReturn(payment);

        paymentService.updatePaymentStatus("TT01", PaymentStatus.FAILED, "TXN_FAIL_TIMEOUT");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("TXN_FAIL_TIMEOUT", payment.getTransactionRef());
        assertNull(payment.getPaidAt());
        verify(em).merge(payment);
        verify(tx).commit();
        verify(em).close();
    }

    @Test
    @DisplayName("Xử lý thanh toán processPayment ném ResourceNotFoundException khi paymentId không tồn tại")
    void processPaymentThrowsNotFoundWhenPaymentDoesNotExist() {
        when(em.find(Payment.class, "UNKNOWN_PAYMENT")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.processPayment("UNKNOWN_PAYMENT"));
        verify(em).close();
    }

    @Test
    void successfulPaymentCannotBeRevertedToFailed() {
        Payment payment = payment(PaymentStatus.SUCCESS);
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        payment.setPaidAt(paidAt);

        assertThrows(BadRequestException.class,
                () -> paymentService.updatePaymentStatus("TT01", PaymentStatus.FAILED, "OTHER"));

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(paidAt, payment.getPaidAt());
        verify(tx).rollback();
    }

    @Test
    void refundPreservesOriginalPaymentTime() {
        Payment payment = payment(PaymentStatus.SUCCESS);
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        payment.setPaidAt(paidAt);

        paymentService.updatePaymentStatus("TT01", PaymentStatus.REFUNDED, "REFUND01");

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(paidAt, payment.getPaidAt());
        verify(tx).commit();
    }

    @Test
    void pendingPaymentCannotBeRefunded() {
        Payment payment = payment(PaymentStatus.PENDING);

        assertThrows(BadRequestException.class,
                () -> paymentService.updatePaymentStatus("TT01", PaymentStatus.REFUNDED, null));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(tx).rollback();
    }

    @Test
    void repeatedSuccessKeepsOriginalPaymentSnapshot() {
        Payment payment = payment(PaymentStatus.SUCCESS);
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 1, 12, 0);
        payment.setPaidAt(paidAt);
        payment.setTransactionRef("ORIGINAL");

        paymentService.updatePaymentStatus("TT01", PaymentStatus.SUCCESS, "DUPLICATE");

        assertEquals(paidAt, payment.getPaidAt());
        assertEquals("ORIGINAL", payment.getTransactionRef());
        verify(em, never()).merge(any());
        verify(tx).commit();
    }

    @Test
    void cancelledOrderCannotBePaid() {
        Payment payment = payment(PaymentStatus.PENDING);
        CustomerOrder order = new CustomerOrder();
        order.setStatus(OrderStatus.CANCELLED);
        payment.setOrder(order);

        assertThrows(BadRequestException.class,
                () -> paymentService.updatePaymentStatus("TT01", PaymentStatus.SUCCESS, null));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertNull(payment.getPaidAt());
        verify(tx).rollback();
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId("TT01");
        payment.setStatus(status);
        when(em.find(Payment.class, "TT01")).thenReturn(payment);
        return payment;
    }
}
