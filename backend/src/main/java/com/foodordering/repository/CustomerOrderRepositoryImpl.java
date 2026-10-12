package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.CustomerOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class CustomerOrderRepositoryImpl implements CustomerOrderRepository {

    @Override
    public CustomerOrder save(CustomerOrder order) {
        EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (em.find(CustomerOrder.class, order.getId()) == null) {
                em.persist(order);
            } else {
                order = em.merge(order);
            }
            tx.commit();
            return order;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Error saving order: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    @Override
    public Optional<CustomerOrder> findById(String id) {
        try (EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager()) {
            // using fetch joins to avoid LazyInitializationException when accessing collections outside session
            String jpql = "SELECT o FROM CustomerOrder o " +
                          "LEFT JOIN FETCH o.orderDetails " +
                          "LEFT JOIN FETCH o.payment " +
                          "WHERE o.id = :id";
            List<CustomerOrder> result = em.createQuery(jpql, CustomerOrder.class)
                    .setParameter("id", id)
                    .getResultList();
            return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
        }
    }

    @Override
    public List<CustomerOrder> findByCustomerId(String customerId) {
        try (EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager()) {
            String jpql = "SELECT o FROM CustomerOrder o WHERE o.customer.id = :customerId ORDER BY o.orderedAt DESC";
            return em.createQuery(jpql, CustomerOrder.class)
                    .setParameter("customerId", customerId)
                    .getResultList();
        }
    }

    @Override
    public List<CustomerOrder> findAll() {
        try (EntityManager em = DatabaseConfig.getEntityManagerFactory().createEntityManager()) {
            String jpql = "SELECT o FROM CustomerOrder o ORDER BY o.orderedAt DESC";
            return em.createQuery(jpql, CustomerOrder.class).getResultList();
        }
    }
}
