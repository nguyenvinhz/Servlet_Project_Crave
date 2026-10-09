package com.foodordering.dto;

import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.PaymentMethod;

public class OrderRequest {
    private FulfillmentType fulfillmentType;
    private String receiverName;
    private String receiverPhone;
    private String deliveryAddress;
    private String customerNote;
    private String promotionId;
    private PaymentMethod paymentMethod;

    public OrderRequest() {}

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
    public String getPromotionId() { return promotionId; }
    public void setPromotionId(String promotionId) { this.promotionId = promotionId; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
}
