package com.foodordering.repository;

import com.foodordering.entity.CartItem;
import com.foodordering.entity.CartItemOption;
import jakarta.persistence.EntityManager;
import java.util.List;

/** Day 1 persistence contract; database operations are implemented on Day 2. */
public class CartItemRepository {
    public List<CartItem> findItemsByCartId(String cartId) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public List<CartItemOption> findOptionsByCartItemId(EntityManager entityManager, String cartItemId) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public List<CartItemOption> findOptionsByCartItemId(String cartItemId) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public CartItem findById(String cartItemId) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public boolean isFoodAvailable(String foodId) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public List<String> getValidOptionIdsForFood(String foodId, List<String> optionIds) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public CartItem findDuplicateItem(String cartId, String foodId, List<String> newOptionIds) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public void insertItemWithOptions(CartItem item, List<String> optionIds) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public void updateQuantity(String cartItemId, int newQuantity) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public void updateNote(String cartItemId, String note) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public void deleteItem(String cartItemId) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }

    public void clearCart(String cartId) {
        throw new UnsupportedOperationException("Cart item persistence is implemented on Day 2.");
    }
}
