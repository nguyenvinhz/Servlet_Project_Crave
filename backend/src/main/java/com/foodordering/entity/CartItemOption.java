package com.foodordering.entity;

/**
 * Entity đại diện cho bảng cart_item_option trong cơ sở dữ liệu.
 */
public class CartItemOption {
    private Long cartItemOptionId;
    private String cartItemId;
    private String optionId;

    public CartItemOption() {
    }

    public CartItemOption(Long cartItemOptionId, String cartItemId, String optionId) {
        this.cartItemOptionId = cartItemOptionId;
        this.cartItemId = cartItemId;
        this.optionId = optionId;
    }

    public CartItemOption(String cartItemId, String optionId) {
        this.cartItemId = cartItemId;
        this.optionId = optionId;
    }

    public Long getCartItemOptionId() {
        return cartItemOptionId;
    }

    public void setCartItemOptionId(Long cartItemOptionId) {
        this.cartItemOptionId = cartItemOptionId;
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public void setCartItemId(String cartItemId) {
        this.cartItemId = cartItemId;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }
}
