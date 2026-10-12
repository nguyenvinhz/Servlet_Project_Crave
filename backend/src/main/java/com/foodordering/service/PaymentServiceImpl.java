package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Payment;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.enums.OrderStatus;
import com.foodordering.exception.ApiException;
import com.foodordering.exception.AppException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.UUID;

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
        updatePaymentStatus(paymentId, PaymentStatus.SUCCESS, "TXN_MOCK_" + UUID.randomUUID());
    }

    @Override
    public void updatePaymentStatus(String paymentId, PaymentStatus status, String transactionRef) {
        if (status == null) {
            throw new BadRequestException("Trạng thái thanh toán không được để trống");
        }
        if (transactionRef != null && transactionRef.trim().length() > 100) {
            throw new BadRequestException("Mã giao dịch không được vượt quá 100 ký tự");
        }
        EntityManager em = getEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Payment payment = em.find(Payment.class, paymentId);
            if (payment == null) {
                throw new ResourceNotFoundException("Thanh toán không tồn tại");
            }
            em.refresh(payment, LockModeType.PESSIMISTIC_WRITE);

            PaymentStatus currentStatus = payment.getStatus();
            if (currentStatus == status) {
                tx.commit();
                return;
            }
            boolean validTransition = switch (currentStatus) {
                case PENDING -> status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED;
                case FAILED -> status == PaymentStatus.PENDING || status == PaymentStatus.SUCCESS;
                case SUCCESS -> status == PaymentStatus.REFUNDED;
                case REFUNDED -> false;
            };
            if (!validTransition) {
                throw new BadRequestException("Không thể chuyển trạng thái thanh toán từ " + currentStatus + " sang " + status);
            }
            if (status == PaymentStatus.SUCCESS && payment.getOrder() != null
                    && payment.getOrder().getStatus() == OrderStatus.CANCELLED) {
                throw new BadRequestException("Không thể xác nhận thanh toán cho đơn hàng đã hủy");
            }

            payment.setStatus(status);
            if (status == PaymentStatus.SUCCESS) {
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
