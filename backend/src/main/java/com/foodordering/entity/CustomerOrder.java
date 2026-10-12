package com.foodordering.entity;

import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity JPA đại diện cho bảng customer_order trong cơ sở dữ liệu Crave.
 * Chứa thông tin đơn hàng, quan hệ với khách hàng, chi tiết đơn, lịch sử trạng thái và thanh toán.
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @Column(name = "order_id", length = 10, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "assigned_employee_id", length = 10)
    private String assignedEmployeeId;

    @Column(name = "promotion_id", length = 10)
    private String promotionId;

    @Column(name = "ordered_at", nullable = false, updatable = false)
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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> orderDetails = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderStatusHistory> statusHistories = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    @Transient
    private String customerId;

    @Transient
    private Promotion promotion;

    public CustomerOrder() {
    }

    public CustomerOrder(String id) {
        this.id = id;
    }

    public CustomerOrder(String id, Customer customer, LocalDateTime orderedAt) {
        this.id = id;
        this.customer = customer;
        this.orderedAt = orderedAt != null ? orderedAt : LocalDateTime.now();
        if (customer != null) {
            this.customerId = customer.getId();
        }
    }

    public CustomerOrder(String id, Customer customer, BigDecimal subtotal, LocalDateTime orderedAt) {
        this.id = id;
        this.customer = customer;
        this.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
        this.orderedAt = orderedAt != null ? orderedAt : LocalDateTime.now();
        if (customer != null) {
            this.customerId = customer.getId();
        }
    }

    @PrePersist
    protected void onCreate() {
        if (orderedAt == null) orderedAt = LocalDateTime.now();
        if (status == null) status = OrderStatus.PENDING_CONFIRMATION;
        if (fulfillmentType == null) fulfillmentType = FulfillmentType.DELIVERY;
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
        if (deliveryFee == null) deliveryFee = BigDecimal.ZERO;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return id;
    }

    public void setOrderId(String orderId) {
        this.id = orderId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        this.customerId = customer != null ? customer.getId() : null;
    }

    public String getCustomerId() {
        if (customer != null) {
            return customer.getId();
        }
        return customerId;
    }

    public void setCustomerId(String customerId) {
        if (customerId == null) {
            setCustomer(null);
        } else if (customer != null && customerId.equals(customer.getId())) {
            this.customerId = customerId;
        } else {
            setCustomer(new Customer(customerId));
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
        this.promotionId = promotion != null ? promotion.getPromotionId() : null;
    }

    public String getPromotionId() {
        return promotionId;
    }

    public void setPromotionId(String promotionId) {
        this.promotionId = promotionId;
        if (promotionId == null || (promotion != null && !promotionId.equals(promotion.getPromotionId()))) {
            this.promotion = null;
        }
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
        return subtotal != null ? subtotal : BigDecimal.ZERO;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal != null ? subtotal : BigDecimal.ZERO;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
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

    public List<OrderDetail> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(List<OrderDetail> orderDetails) {
        this.orderDetails = orderDetails;
    }

    public List<OrderStatusHistory> getStatusHistories() {
        return statusHistories;
    }

    public void setStatusHistories(List<OrderStatusHistory> statusHistories) {
        this.statusHistories = statusHistories;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public void addOrderDetail(OrderDetail detail) {
        this.orderDetails.add(detail);
        detail.setOrder(this);
    }

    public void addStatusHistory(OrderStatusHistory history) {
        this.statusHistories.add(history);
        history.setOrder(this);
    }

    public void setPaymentHelper(Payment payment) {
        this.payment = payment;
        if (payment != null) {
            payment.setOrder(this);
        }
    }
}
