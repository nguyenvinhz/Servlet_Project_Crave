package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Payment;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;

public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public void processPayment(String paymentId) {
        updatePaymentStatus(paymentId, PaymentStatus.SUCCESS, "TXN_MOCK_" + System.currentTimeMillis());
    }

    @Override
    public void updatePaymentStatus(String paymentId, PaymentStatus status, String transactionRef) {
        EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Payment payment = em.find(Payment.class, paymentId);
            if (payment == null) {
                throw new RuntimeException("Thanh toán không tồn tại");
            }

            payment.setStatus(status);
            if (status == PaymentStatus.SUCCESS || status == PaymentStatus.REFUNDED) {
                payment.setPaidAt(LocalDateTime.now());
            }
            if (transactionRef != null) {
                payment.setTransactionRef(transactionRef);
            }
            em.merge(payment);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi cập nhật thanh toán: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }
}
