package com.foodordering.bean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CartBean {
    public CartBean(com.foodordering.model.Cart cart, List<com.foodordering.model.CartItem> items) {
        this.cartId = cart.getCartId();
        this.customerId = cart.getCustomer() != null ? cart.getCustomer().getId() : cart.getCustomerId();
        this.items = items == null ? List.of() : items.stream().map(CartItemBean::new).toList();
    }
    private String cartId;
    private String customerId;
    private List<CartItemBean> items = new ArrayList<>();
    private int totalItems = 0;
    private BigDecimal subtotal = BigDecimal.ZERO;

    public CartBean() {
    }

    public CartBean(String cartId, String customerId) {
        this.cartId = cartId;
        this.customerId = customerId;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public List<CartItemBean> getItems() {
        return items;
    }

    public void setItems(List<CartItemBean> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
