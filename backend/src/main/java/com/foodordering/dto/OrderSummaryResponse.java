package com.foodordering.dto;

import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderSummaryResponse {
    private String orderId;
    private LocalDateTime orderedAt;
    private FulfillmentType fulfillmentType;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String paymentStatus;

    public OrderSummaryResponse() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public LocalDateTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(LocalDateTime orderedAt) { this.orderedAt = orderedAt; }

    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(FulfillmentType fulfillmentType) { this.fulfillmentType = fulfillmentType; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}
