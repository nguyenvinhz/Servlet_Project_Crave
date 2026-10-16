package com.foodordering.dao;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.model.Cart;
import com.foodordering.model.Customer;
import com.foodordering.util.IdGenerator;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CartDAO {

    public int countItemsByCustomerId(String customerId) {
        try (EntityManager em = DatabaseConfig.getEntityManager()) {
            Long quantity = em.createQuery("""
                    SELECT COALESCE(SUM(item.quantity), 0)
                    FROM CartItem item WHERE item.cart.customer.id = :customerId
                    """, Long.class).setParameter("customerId", customerId).getSingleResult();
            return Math.toIntExact(quantity);
        }
    }

    public Cart findByCustomerId(String customerId) {
        if (customerId == null) return null;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return null;
        try {
            List<Cart> list = em.createQuery("SELECT c FROM Cart c WHERE c.customer.id = :customerId", Cart.class)
                    .setParameter("customerId", customerId)
                    .getResultList();
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi JPA khi tìm giỏ hàng theo customerId: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    public Cart findById(String cartId) {
        if (cartId == null) return null;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return null;
        try {
            return em.find(Cart.class, cartId);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi JPA khi tìm giỏ hàng theo cartId: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    public Cart createCart(String customerId) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) {
            Cart fallback = new Cart();
            fallback.setCartId(IdGenerator.generateCartId());
            fallback.setCustomerId(customerId);
            return fallback;
        }
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Customer customerRef = em.find(Customer.class, customerId, LockModeType.PESSIMISTIC_WRITE);
            if (customerRef == null) {
                throw new ResourceNotFoundException("Khách hàng không tồn tại");
            }
            List<Cart> existing = em.createQuery("SELECT c FROM Cart c WHERE c.customer.id = :customerId", Cart.class)
                    .setParameter("customerId", customerId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .getResultList();
            if (!existing.isEmpty()) {
                tx.commit();
                return existing.get(0);
            }
            String newCartId = IdGenerator.generateCartId();
            Cart cart = new Cart();
            cart.setCartId(newCartId);
            cart.setCustomer(customerRef);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            em.persist(cart);
            tx.commit();
            return cart;
        } catch (BusinessException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi JPA khi tạo mới giỏ hàng: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    public BigDecimal getCartSubtotal(String cartId) {
        if (cartId == null) return BigDecimal.ZERO;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return BigDecimal.ZERO;
        try {
            List<?> results = em.createNativeQuery("SELECT subtotal FROM v_cart_summary WHERE cart_id = :cartId")
                    .setParameter("cartId", cartId)
                    .getResultList();
            if (!results.isEmpty() && results.get(0) != null) {
                return new BigDecimal(results.get(0).toString());
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        } finally {
            em.close();
        }
    }

}
