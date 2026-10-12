package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Cart;
import com.foodordering.entity.Customer;
import com.foodordering.utils.IdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CartRepository {

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
            String newCartId = getNextCartId(em);
            Customer customerRef = em.getReference(Customer.class, customerId);
            Cart cart = new Cart();
            cart.setCartId(newCartId);
            cart.setCustomer(customerRef);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            em.persist(cart);
            tx.commit();
            return cart;
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

    private String getNextCartId(EntityManager em) {
        try {
            List<String> lastIds = em.createQuery("SELECT c.cartId FROM Cart c ORDER BY c.cartId DESC", String.class)
                    .setMaxResults(1)
                    .getResultList();
            if (!lastIds.isEmpty()) {
                String lastId = lastIds.get(0);
                if (lastId != null && lastId.startsWith("GH")) {
                    int num = Integer.parseInt(lastId.substring(2)) + 1;
                    return String.format("GH%02d", num);
                }
            }
        } catch (Exception ignored) {
        }
        return IdGenerator.generateCartId();
    }
}
