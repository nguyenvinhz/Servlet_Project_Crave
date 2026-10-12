package com.foodordering.dto;

import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private List<OrderItemResponse> items;
    private PaymentSummary payment;
    private List<StatusHistoryEntry> history;

    public OrderResponse() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public LocalDateTime getOrderedAt() { return orderedAt; }
    public void setOrderedAt(LocalDateTime orderedAt) { this.orderedAt = orderedAt; }

    public FulfillmentType getFulfillmentType() { return fulfillmentType; }
    public void setFulfillmentType(FulfillmentType fulfillmentType) { this.fulfillmentType = fulfillmentType; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getCustomerNote() { return customerNote; }
    public void setCustomerNote(String customerNote) { this.customerNote = customerNote; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public List<OrderItemResponse> getItems() { return items; }
    public void setItems(List<OrderItemResponse> items) { this.items = items; }

    public PaymentSummary getPayment() { return payment; }
    public void setPayment(PaymentSummary payment) { this.payment = payment; }

    public List<StatusHistoryEntry> getHistory() { return history; }
    public void setHistory(List<StatusHistoryEntry> history) { this.history = history; }

    public static class OrderItemResponse {
        private String foodId;
        private String foodName;
        private String foodNameSnapshot;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private String note;
        private List<OptionSnapshot> options;

        public String getFoodId() { return foodId; }
        public void setFoodId(String foodId) { this.foodId = foodId; }

        public String getFoodName() { return foodName; }
        public void setFoodName(String foodName) { this.foodName = foodName; }

        public String getFoodNameSnapshot() { return foodNameSnapshot; }
        public void setFoodNameSnapshot(String foodNameSnapshot) { this.foodNameSnapshot = foodNameSnapshot; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

        public BigDecimal getLineTotal() { return lineTotal; }
        public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }

        public List<OptionSnapshot> getOptions() { return options; }
        public void setOptions(List<OptionSnapshot> options) { this.options = options; }
    }

    public static class OptionSnapshot {
        private String optionName;
        private BigDecimal extraPrice;

        public String getOptionName() { return optionName; }
        public void setOptionName(String optionName) { this.optionName = optionName; }

        public BigDecimal getExtraPrice() { return extraPrice; }
        public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }
    }

    public static class PaymentSummary {
        private String paymentId;
        private String method;
        private String status;
        private BigDecimal amount;

        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public static class StatusHistoryEntry {
        private String status;
        private LocalDateTime changedAt;
        private String note;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getChangedAt() { return changedAt; }
        public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
