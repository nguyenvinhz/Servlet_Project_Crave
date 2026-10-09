package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Cart;
import com.foodordering.entity.CartItem;
import com.foodordering.entity.CartItemOption;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.FoodStatus;
import com.foodordering.utils.IdGenerator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Repository xử lý các thao tác dữ liệu đối với bảng cart_item và cart_item_option sử dụng JPA (non-Spring Boot).
 * Tuân thủ nguyên tắc tầng Repository: chỉ trả về Entity, không phụ thuộc hay trả về DTO.
 */
public class CartItemRepository {

    /**
     * Lấy danh sách Entity CartItem trong giỏ kèm Food và các CartItemOption liên quan.
     */
    public List<CartItem> findItemsByCartId(String cartId) {
        if (cartId == null) return Collections.emptyList();
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return Collections.emptyList();
        try {
            String jpql = "SELECT DISTINCT ci FROM CartItem ci LEFT JOIN FETCH ci.food f WHERE ci.cart.cartId = :cartId ORDER BY ci.createdAt ASC";
            List<CartItem> items = em.createQuery(jpql, CartItem.class)
                    .setParameter("cartId", cartId)
                    .getResultList();

            for (CartItem ci : items) {
                List<CartItemOption> options = findOptionsByCartItemId(em, ci.getCartItemId());
                ci.setItemOptions(options);
            }
            return items;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi JPA khi lấy danh sách món trong giỏ: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Lấy danh sách Entity CartItemOption theo cartItemId (sử dụng cùng EntityManager).
     */
    public List<CartItemOption> findOptionsByCartItemId(EntityManager em, String cartItemId) {
        if (cartItemId == null || em == null) return Collections.emptyList();
        try {
            String jpql = "SELECT cio FROM CartItemOption cio LEFT JOIN FETCH cio.option fo WHERE cio.cartItem.cartItemId = :cartItemId";
            return em.createQuery(jpql, CartItemOption.class)
                    .setParameter("cartItemId", cartItemId)
                    .getResultList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Lấy danh sách Entity CartItemOption theo cartItemId (mở EntityManager mới).
     */
    public List<CartItemOption> findOptionsByCartItemId(String cartItemId) {
        if (cartItemId == null) return Collections.emptyList();
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return Collections.emptyList();
        try {
            return findOptionsByCartItemId(em, cartItemId);
        } finally {
            em.close();
        }
    }

    /**
     * Tìm món trong giỏ theo ID.
     */
    public CartItem findById(String cartItemId) {
        if (cartItemId == null) return null;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return null;
        try {
            return em.find(CartItem.class, cartItemId);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi JPA khi tìm cart item: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Kiểm tra món ăn có tồn tại và đang AVAILABLE hay không qua Food entity.
     */
    public boolean isFoodAvailable(String foodId) {
        if (foodId == null) return false;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return true; // Trong test/offline mode
        try {
            Food food = em.find(Food.class, foodId);
            return food != null && food.getStatus() == FoodStatus.AVAILABLE;
        } catch (Exception e) {
            return false;
        } finally {
            em.close();
        }
    }

    /**
     * Xác thực các option có thuộc món ăn foodId và đang ACTIVE hay không.
     */
    public List<String> getValidOptionIdsForFood(String foodId, List<String> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return Collections.emptyList();
        }
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return optionIds;
        try {
            String jpql = "SELECT fo.id FROM FoodOption fo WHERE fo.food.id = :foodId AND fo.status = :status AND fo.id IN (:optionIds)";
            return em.createQuery(jpql, String.class)
                    .setParameter("foodId", foodId)
                    .setParameter("status", com.foodordering.enums.OptionStatus.ACTIVE)
                    .setParameter("optionIds", optionIds)
                    .getResultList();
        } catch (Exception e) {
            return Collections.emptyList();
        } finally {
            em.close();
        }
    }

    /**
     * Tìm món đã có trong giỏ có cùng foodId và tập hợp optionIds y hệt nhau để cộng dồn số lượng.
     */
    public CartItem findDuplicateItem(String cartId, String foodId, List<String> newOptionIds) {
        if (cartId == null || foodId == null) return null;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return null;
        try {
            List<CartItem> items = em.createQuery("SELECT ci FROM CartItem ci WHERE ci.cart.cartId = :cartId AND ci.food.id = :foodId", CartItem.class)
                    .setParameter("cartId", cartId)
                    .setParameter("foodId", foodId)
                    .getResultList();

            Set<String> targetSet = new HashSet<>(newOptionIds != null ? newOptionIds : Collections.emptyList());

            for (CartItem item : items) {
                Set<String> existingOptions = getOptionIdsForItem(em, item.getCartItemId());
                if (existingOptions.equals(targetSet)) {
                    return item;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            em.close();
        }
    }

    private Set<String> getOptionIdsForItem(EntityManager em, String cartItemId) {
        Set<String> set = new HashSet<>();
        try {
            String jpql = "SELECT cio.option.id FROM CartItemOption cio WHERE cio.cartItem.cartItemId = :cartItemId";
            List<String> list = em.createQuery(jpql, String.class)
                    .setParameter("cartItemId", cartItemId)
                    .getResultList();
            set.addAll(list);
        } catch (Exception ignored) {
        }
        return set;
    }

    /**
     * Thêm món mới vào giỏ hàng và lưu các options trong một JPA transaction duy nhất.
     */
    public void insertItemWithOptions(CartItem item, List<String> optionIds) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return;
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            if (item.getCartItemId() == null || item.getCartItemId().trim().isEmpty()) {
                item.setCartItemId(getNextCartItemId(em));
            }

            Cart cartRef = em.getReference(Cart.class, item.getCartId());
            item.setCart(cartRef);

            Food foodRef = em.getReference(Food.class, item.getFoodId());
            item.setFood(foodRef);

            em.persist(item);

            if (optionIds != null && !optionIds.isEmpty()) {
                for (String optId : optionIds) {
                    FoodOption optionRef = em.getReference(FoodOption.class, optId);
                    CartItemOption opt = new CartItemOption(item, optionRef);
                    em.persist(opt);
                }
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi JPA khi thêm món vào giỏ hàng: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Cập nhật số lượng món trong giỏ hàng.
     */
    public void updateQuantity(String cartItemId, int newQuantity) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return;
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            CartItem item = em.find(CartItem.class, cartItemId);
            if (item != null) {
                item.setQuantity(newQuantity);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi JPA khi cập nhật số lượng món: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Cập nhật ghi chú món trong giỏ hàng.
     */
    public void updateNote(String cartItemId, String note) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return;
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            CartItem item = em.find(CartItem.class, cartItemId);
            if (item != null) {
                item.setNote(note);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi JPA khi cập nhật ghi chú món: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Xóa một món khỏi giỏ hàng.
     */
    public void deleteItem(String cartItemId) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return;
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            CartItem item = em.find(CartItem.class, cartItemId);
            if (item != null) {
                em.remove(item);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi JPA khi xóa món khỏi giỏ hàng: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Xóa toàn bộ món trong giỏ hàng của cartId.
     */
    public void clearCart(String cartId) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return;
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.createQuery("DELETE FROM CartItem ci WHERE ci.cart.cartId = :cartId")
                    .setParameter("cartId", cartId)
                    .executeUpdate();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw new RuntimeException("Lỗi JPA khi làm sạch giỏ hàng: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    private String getNextCartItemId(EntityManager em) {
        try {
            List<String> lastIds = em.createQuery("SELECT ci.cartItemId FROM CartItem ci ORDER BY ci.cartItemId DESC", String.class)
                    .setMaxResults(1)
                    .getResultList();
            if (!lastIds.isEmpty()) {
                String lastId = lastIds.get(0);
                if (lastId != null && lastId.startsWith("CTGH")) {
                    try {
                        int num = Integer.parseInt(lastId.substring(4));
                        return String.format("CTGH%02d", num + 1);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return IdGenerator.generateCartItemId();
    }
}
