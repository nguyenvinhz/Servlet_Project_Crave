package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Payment;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.ApiException;
import com.foodordering.exception.AppException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;

public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final EntityManagerFactory emf;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this(paymentRepository, null);
    }

    public PaymentServiceImpl(PaymentRepository paymentRepository, EntityManagerFactory emf) {
        this.paymentRepository = paymentRepository;
        this.emf = emf;
    }

    private EntityManager getEntityManager() {
        return emf != null ? emf.createEntityManager() : DatabaseConfig.getEntityManagerFactory().createEntityManager();
    }

    @Override
    public void processPayment(String paymentId) {
        updatePaymentStatus(paymentId, PaymentStatus.SUCCESS, "TXN_MOCK_" + System.currentTimeMillis());
    }

    @Override
    public void updatePaymentStatus(String paymentId, PaymentStatus status, String transactionRef) {
        EntityManager em = getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Payment payment = em.find(Payment.class, paymentId);
            if (payment == null) {
                throw new ResourceNotFoundException("Thanh toán không tồn tại");
            }

            payment.setStatus(status);
            if (status == PaymentStatus.SUCCESS || status == PaymentStatus.REFUNDED) {
                payment.setPaidAt(LocalDateTime.now());
            }
            if (transactionRef != null && !transactionRef.trim().isEmpty()) {
                payment.setTransactionRef(transactionRef.trim());
            }
            em.merge(payment);
            tx.commit();
        } catch (ApiException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (AppException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi cập nhật thanh toán: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }
}
