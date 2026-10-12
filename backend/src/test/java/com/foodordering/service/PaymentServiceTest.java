package com.foodordering.service;

import com.foodordering.entity.Payment;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentRepository paymentRepository;
    private EntityManagerFactory emf;
    private EntityManager em;
    private EntityTransaction tx;
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        emf = mock(EntityManagerFactory.class);
        em = mock(EntityManager.class);
        tx = mock(EntityTransaction.class);

        when(emf.createEntityManager()).thenReturn(em);
        when(em.getTransaction()).thenReturn(tx);

        paymentService = new PaymentServiceImpl(paymentRepository, emf);
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
}
