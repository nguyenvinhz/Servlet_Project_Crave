package com.foodordering.bean;

import com.foodordering.model.Food;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * bean đại diện cho một mục món ăn trong giỏ hàng (chỉ chứa các trường dữ liệu và phương thức get/set).
 */
public class CartItemBean {
    public CartItemBean(com.foodordering.model.CartItem entity) {

        this.setCartItemId(entity.getCartItemId());
        this.setCartId(entity.getCart() != null ? entity.getCart().getCartId() : entity.getCartId());
        this.setQuantity(entity.getQuantity());
        this.setNote(entity.getNote());

        Food food = entity.getFood();
        if (food != null) {
            this.setFoodId(food.getFoodId());
            this.setFoodName(food.getName());
            this.setImageUrl(food.getImageUrl());
            this.setBasePrice(food.getBasePrice() != null ? food.getBasePrice() : BigDecimal.ZERO);
        } else {
            this.setFoodId(entity.getFoodId());
            this.setBasePrice(BigDecimal.ZERO);
        }

        List<CartItemOptionBean> options = entity.getItemOptions() == null ? java.util.List.of() : entity.getItemOptions().stream().map(CartItemOptionBean::new).toList();
        this.setOptions(options);

    }

    private String cartItemId;
    private String cartId;
    private String foodId;
    private String foodName;
    private String imageUrl;
    private BigDecimal basePrice;
    private int quantity;
    private String note;
    private List<CartItemOptionBean> options = new ArrayList<>();
    private BigDecimal optionTotal = BigDecimal.ZERO;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public CartItemBean() {
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public void setCartItemId(String cartItemId) {
        this.cartItemId = cartItemId;
    }

    public String getCartId() {
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
    }

    public String getFoodId() {
        return foodId;
    }

    public void setFoodId(String foodId) {
        this.foodId = foodId;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<CartItemOptionBean> getOptions() {
        return options;
    }

    public void setOptions(List<CartItemOptionBean> options) {
        this.options = options != null ? options : new ArrayList<>();
    }

    public BigDecimal getOptionTotal() {
        return optionTotal;
    }

    public void setOptionTotal(BigDecimal optionTotal) {
        this.optionTotal = optionTotal;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
