package com.foodordering.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho giỏ hàng hoàn chỉnh của khách hàng.
 * Đảm bảo nguyên tắc bảo mật: toàn bộ số lượng món, tổng phụ phụ và tổng tiền
 * đều được tính toán từ cơ sở dữ liệu server-side, không tin cậy dữ liệu do client gửi lên.
 */
public class CartDto {
    private String cartId;
    private String customerId;
    private List<CartItemDto> items = new ArrayList<>();
    private int totalItems = 0;
    private BigDecimal subtotal = BigDecimal.ZERO;

    public CartDto() {
    }

    public CartDto(String cartId, String customerId) {
        this.cartId = cartId;
        this.customerId = customerId;
    }

    /**
     * Tự động tính toán lại totalItems và subtotal từ danh sách items.
     */
    public void recalculate() {
        int count = 0;
        BigDecimal sum = BigDecimal.ZERO;
        if (items != null) {
            for (CartItemDto item : items) {
                item.calculateTotals();
                count += item.getQuantity();
                if (item.getLineTotal() != null) {
                    sum = sum.add(item.getLineTotal());
                }
            }
        }
        this.totalItems = count;
        this.subtotal = sum;
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

    public List<CartItemDto> getItems() {
        return items;
    }

    public void setItems(List<CartItemDto> items) {
        this.items = items != null ? items : new ArrayList<>();
        recalculate();
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
