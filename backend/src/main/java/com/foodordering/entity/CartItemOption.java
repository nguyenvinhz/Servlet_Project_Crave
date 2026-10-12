package com.foodordering.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

/**
 * Entity JPA đại diện cho bảng cart_item_option trong cơ sở dữ liệu Crave.
 */
@Entity
@Table(name = "cart_item_option", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cart_item_id", "option_id"})
})
public class CartItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_option_id", nullable = false)
    private Long cartItemOptionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_item_id", nullable = false)
    private CartItem cartItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id", nullable = false)
    private FoodOption option;

    @Transient
    private String cartItemId;

    @Transient
    private String optionId;

    public CartItemOption() {
    }

    public CartItemOption(Long cartItemOptionId, String cartItemId, String optionId) {
        this.cartItemOptionId = cartItemOptionId;
        this.cartItemId = cartItemId;
        this.optionId = optionId;
        if (optionId != null) {
            this.option = new FoodOption(optionId);
        }
    }

    public CartItemOption(CartItem cartItem, FoodOption option) {
        this.cartItem = cartItem;
        this.option = option;
        if (cartItem != null) {
            this.cartItemId = cartItem.getCartItemId();
        }
        if (option != null) {
            this.optionId = option.getOptionId();
        }
    }

    public CartItemOption(CartItem cartItem, String optionId) {
        this.cartItem = cartItem;
        if (cartItem != null) {
            this.cartItemId = cartItem.getCartItemId();
        }
        this.optionId = optionId;
        if (optionId != null) {
            this.option = new FoodOption(optionId);
        }
    }

    public CartItemOption(String cartItemId, String optionId) {
        this.cartItemId = cartItemId;
        this.optionId = optionId;
        if (optionId != null) {
            this.option = new FoodOption(optionId);
        }
    }

    public Long getCartItemOptionId() {
        return cartItemOptionId;
    }

    public void setCartItemOptionId(Long cartItemOptionId) {
        this.cartItemOptionId = cartItemOptionId;
    }

    public CartItem getCartItem() {
        return cartItem;
    }

    public void setCartItem(CartItem cartItem) {
        this.cartItem = cartItem;
        if (cartItem != null) {
            this.cartItemId = cartItem.getCartItemId();
        }
    }

    public String getCartItemId() {
        if (cartItem != null) {
            return cartItem.getCartItemId();
        }
        return cartItemId;
    }

    public void setCartItemId(String cartItemId) {
        this.cartItemId = cartItemId;
    }

    public FoodOption getOption() {
        return option;
    }

    public void setOption(FoodOption option) {
        this.option = option;
        if (option != null) {
            this.optionId = option.getOptionId();
        }
    }

    public String getOptionId() {
        if (option != null) {
            return option.getOptionId();
        }
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
        if (option == null && optionId != null) {
            this.option = new FoodOption(optionId);
        }
    }
}
