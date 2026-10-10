package com.foodordering.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity JPA đại diện cho bảng cart_item trong cơ sở dữ liệu Crave.
 */
@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @Column(name = "cart_item_id", length = 10, nullable = false)
    private String cartItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "note", length = 255)
    private String note;

    @OneToMany(mappedBy = "cartItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItemOption> itemOptions = new ArrayList<>();

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Transient
    private String cartId;

    @Transient
    private String foodId;

    @Transient
    private List<String> optionIds = new ArrayList<>();

    public CartItem() {
    }

    public CartItem(String cartItemId, String cartId, String foodId, int quantity, String note) {
        this.cartItemId = cartItemId;
        this.cartId = cartId;
        this.foodId = foodId;
        this.quantity = quantity;
        this.note = note;
        if (cartId != null) {
            this.cart = new Cart(cartId, (Customer) null);
        }
        if (foodId != null) {
            this.food = new Food(foodId);
        }
    }

    public CartItem(String cartItemId, Cart cart, Food food, int quantity, String note) {
        this.cartItemId = cartItemId;
        this.cart = cart;
        this.food = food;
        this.quantity = quantity;
        this.note = note;
        if (cart != null) this.cartId = cart.getCartId();
        if (food != null) this.foodId = food.getFoodId();
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public void setCartItemId(String cartItemId) {
        this.cartItemId = cartItemId;
    }

    public Cart getCart() {
        return cart;
    }

    public void setCart(Cart cart) {
        this.cart = cart;
        if (cart != null) {
            this.cartId = cart.getCartId();
        }
    }

    public String getCartId() {
        if (cart != null) {
            return cart.getCartId();
        }
        return cartId;
    }

    public void setCartId(String cartId) {
        this.cartId = cartId;
        if (cart == null && cartId != null) {
            this.cart = new Cart(cartId, (Customer) null);
        }
    }

    public Food getFood() {
        return food;
    }

    public void setFood(Food food) {
        this.food = food;
        if (food != null) {
            this.foodId = food.getFoodId();
        }
    }

    public String getFoodId() {
        if (food != null) {
            return food.getFoodId();
        }
        return foodId;
    }

    public void setFoodId(String foodId) {
        this.foodId = foodId;
        if (food == null && foodId != null) {
            this.food = new Food(foodId);
        }
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

    public List<CartItemOption> getItemOptions() {
        return itemOptions;
    }

    public void setItemOptions(List<CartItemOption> itemOptions) {
        this.itemOptions = itemOptions != null ? itemOptions : new ArrayList<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getOptionIds() {
        if (optionIds != null && !optionIds.isEmpty()) {
            return optionIds;
        }
        if (itemOptions != null && !itemOptions.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (CartItemOption opt : itemOptions) {
                if (opt.getOption() != null) {
                    ids.add(opt.getOption().getOptionId());
                } else if (opt.getOptionId() != null) {
                    ids.add(opt.getOptionId());
                }
            }
            return ids;
        }
        return new ArrayList<>();
    }

    public void setOptionIds(List<String> optionIds) {
        this.optionIds = optionIds != null ? optionIds : new ArrayList<>();
    }
}
