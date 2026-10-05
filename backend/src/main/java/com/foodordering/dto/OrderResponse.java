package com.foodordering.dto;

import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderResponse {
    private String orderId;
    private String customerId;
    private LocalDateTime orderedAt;
    private FulfillmentType fulfillmentType;
    private String receiverName;
    private String receiverPhone;
    private String deliveryAddress;
    private String customerNote;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal deliveryFee;
    private BigDecimal totalAmount;
    private OrderStatus status;

    // Constructors, Getters, Setters có thể được tạo sau khi chốt xong
    public OrderResponse() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    // ... Các setter/getter khác
}
