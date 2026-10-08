package com.foodordering.entity;

import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity JPA đại diện cho bảng customer_order trong cơ sở dữ liệu Crave.
 * Chứa trường ordered_at (orderTime) để phục vụ việc xác thực thời gian đặt hàng của khách hàng với hạn của promotion.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @Column(name = "order_id", length = 10, nullable = false)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "assigned_employee_id", length = 10)
    private String assignedEmployeeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false)
    private FulfillmentType fulfillmentType = FulfillmentType.DELIVERY;

    @Column(name = "receiver_name", length = 100, nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", length = 15, nullable = false)
    private String receiverPhone;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Column(name = "customer_note", length = 255)
    private String customerNote;

    @Column(name = "subtotal", nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @Column(name = "total_amount", insertable = false, updatable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING_CONFIRMATION;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Transient
    private String customerId;

    @Transient
    private String promotionId;

    public CustomerOrder() {
    }

    public CustomerOrder(String orderId) {
        this.orderId = orderId;
    }

    public CustomerOrder(String orderId, Customer customer, LocalDateTime orderedAt) {
        this.orderId = orderId;
        this.customer = customer;
        this.orderedAt = orderedAt != null ? orderedAt : LocalDateTime.now();
        if (customer != null) {
            this.customerId = customer.getId();
        }
    }

    public CustomerOrder(String orderId, Customer customer, BigDecimal subtotal, LocalDateTime orderedAt) {
        this.orderId = orderId;
        this.customer = customer;
        this.subtotal = subtotal;
        this.orderedAt = orderedAt != null ? orderedAt : LocalDateTime.now();
        if (customer != null) {
            this.customerId = customer.getId();
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null) {
            this.customerId = customer.getId();
        }
    }

    public String getCustomerId() {
        if (customer != null) {
            return customer.getId();
        }
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
        if (customer == null && customerId != null) {
            this.customer = new Customer(customerId);
        }
    }

    public String getAssignedEmployeeId() {
        return assignedEmployeeId;
    }

    public void setAssignedEmployeeId(String assignedEmployeeId) {
        this.assignedEmployeeId = assignedEmployeeId;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public void setPromotion(Promotion promotion) {
        this.promotion = promotion;
        if (promotion != null) {
            this.promotionId = promotion.getPromotionId();
        }
    }

    public String getPromotionId() {
        if (promotion != null) {
            return promotion.getPromotionId();
        }
        return promotionId;
    }

    public void setPromotionId(String promotionId) {
        this.promotionId = promotionId;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public void setOrderedAt(LocalDateTime orderedAt) {
        this.orderedAt = orderedAt;
    }

    /**
     * Alias getter cho orderedAt theo tên gọi orderTime của nghiệp vụ.
     */
    public LocalDateTime getOrderTime() {
        return orderedAt;
    }

    /**
     * Alias setter cho orderedAt theo tên gọi orderTime của nghiệp vụ.
     */
    public void setOrderTime(LocalDateTime orderTime) {
        this.orderedAt = orderTime;
    }

    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    public void setFulfillmentType(FulfillmentType fulfillmentType) {
        this.fulfillmentType = fulfillmentType;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public void setDeliveryFee(BigDecimal deliveryFee) {
        this.deliveryFee = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
    }

    public BigDecimal getTotalAmount() {
        if (totalAmount != null) {
            return totalAmount;
        }
        return (subtotal != null ? subtotal : BigDecimal.ZERO)
                .subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO)
                .add(deliveryFee != null ? deliveryFee : BigDecimal.ZERO)
                .max(BigDecimal.ZERO);
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
