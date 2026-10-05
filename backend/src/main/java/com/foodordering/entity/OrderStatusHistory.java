package com.foodordering.entity;

import com.foodordering.enums.OrderStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @Column(name = "history_id", length = 10, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by_employee_id", length = 10)
    private String changedByEmployeeId;

    @Column(name = "note")
    private String note;

    public OrderStatusHistory() {}

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) changedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public CustomerOrder getOrder() { return order; }
    public void setOrder(CustomerOrder order) { this.order = order; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
    public String getChangedByEmployeeId() { return changedByEmployeeId; }
    public void setChangedByEmployeeId(String changedByEmployeeId) { this.changedByEmployeeId = changedByEmployeeId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
