package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;
import java.util.Optional;

public class PaymentRepositoryImpl implements PaymentRepository {

    @Override
    public Payment save(Payment payment) {
        EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (em.find(Payment.class, payment.getId()) == null) {
                em.persist(payment);
            } else {
                payment = em.merge(payment);
            }
            tx.commit();
            return payment;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Error saving payment: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    @Override
    public Optional<Payment> findById(String id) {
        try (EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager()) {
            return Optional.ofNullable(em.find(Payment.class, id));
        }
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        try (EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager()) {
            String jpql = "SELECT p FROM Payment p WHERE p.order.id = :orderId";
            List<Payment> result = em.createQuery(jpql, Payment.class)
                    .setParameter("orderId", orderId)
                    .getResultList();
            return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
        }
    }
}
