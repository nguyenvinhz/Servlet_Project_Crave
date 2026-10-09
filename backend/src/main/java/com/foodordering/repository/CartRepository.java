package com.foodordering.repository;

import com.foodordering.entity.Cart;
import java.math.BigDecimal;

/** Day 1 persistence contract; database operations are implemented on Day 2. */
public class CartRepository {
    public Cart findByCustomerId(String customerId) {
        throw new UnsupportedOperationException("Cart persistence is implemented on Day 2.");
    }

    public Cart findById(String cartId) {
        throw new UnsupportedOperationException("Cart persistence is implemented on Day 2.");
    }

    public Cart createCart(String customerId) {
        throw new UnsupportedOperationException("Cart persistence is implemented on Day 2.");
    }

    public BigDecimal getCartSubtotal(String cartId) {
        throw new UnsupportedOperationException("Cart persistence is implemented on Day 2.");
    }
}
