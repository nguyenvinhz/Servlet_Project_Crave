package com.foodordering.validator;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.UpdateCartItemRequest;

/** Day 1 validation contract; input rules are implemented on Day 2. */
public final class CartValidator {
    private CartValidator() {
    }

    public static void validateCustomerId(String customerId) {
        throw new UnsupportedOperationException("Cart validation is implemented on Day 2.");
    }

    public static void validateAddToCart(AddToCartRequest request) {
        throw new UnsupportedOperationException("Cart validation is implemented on Day 2.");
    }

    public static void validateUpdateCartItem(UpdateCartItemRequest request) {
        throw new UnsupportedOperationException("Cart validation is implemented on Day 2.");
    }

    public static void validateCartItemId(String cartItemId) {
        throw new UnsupportedOperationException("Cart validation is implemented on Day 2.");
    }
}
