package com.foodordering.service;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.CartItemDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.mapper.CartItemMapper;
import com.foodordering.mapper.CartMapper;
import com.foodordering.repository.CartItemRepository;
import com.foodordering.repository.CartRepository;
import java.math.BigDecimal;

/** Day 1 business contract; cart operations and totals are implemented on Day 2. */
public class CartService {
    public CartService() {
    }

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository) {
    }

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       CartMapper cartMapper, CartItemMapper cartItemMapper) {
    }

    public CartDto getOrCreateCart(String customerId) {
        throw new UnsupportedOperationException("Cart business logic is implemented on Day 2.");
    }

    public CartDto addItem(String customerId, AddToCartRequest request) {
        throw new UnsupportedOperationException("Cart business logic is implemented on Day 2.");
    }

    public CartDto updateItem(String customerId, UpdateCartItemRequest request) {
        throw new UnsupportedOperationException("Cart business logic is implemented on Day 2.");
    }

    public CartDto removeItem(String customerId, String cartItemId) {
        throw new UnsupportedOperationException("Cart business logic is implemented on Day 2.");
    }

    public CartDto clearCart(String customerId) {
        throw new UnsupportedOperationException("Cart business logic is implemented on Day 2.");
    }

    public BigDecimal getVerifiedSubtotal(String customerId) {
        throw new UnsupportedOperationException("Cart business logic is implemented on Day 2.");
    }

    public void calculateItemTotals(CartItemDto item) {
        throw new UnsupportedOperationException("Cart totals are implemented on Day 2.");
    }

    public void calculateCartTotals(CartDto cart) {
        throw new UnsupportedOperationException("Cart totals are implemented on Day 2.");
    }
}
