package com.foodordering.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_detail")
public class OrderDetail {

    @Id
    @Column(name = "order_detail_id", length = 10, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    // Lưu tạm Food ID (Huân chưa tạo Food)
    @Column(name = "food_id", length = 10, nullable = false)
    private String foodId;

    @Column(name = "food_name_snapshot", length = 150, nullable = false)
    private String foodNameSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", insertable = false, updatable = false)
    private BigDecimal lineTotal;

    @Column(name = "note")
    private String note;

    @OneToMany(mappedBy = "orderDetail", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetailOption> options = new ArrayList<>();

    public OrderDetail() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public CustomerOrder getOrder() { return order; }
    public void setOrder(CustomerOrder order) { this.order = order; }
    public String getFoodId() { return foodId; }
    public void setFoodId(String foodId) { this.foodId = foodId; }
    public String getFoodNameSnapshot() { return foodNameSnapshot; }
    public void setFoodNameSnapshot(String foodNameSnapshot) { this.foodNameSnapshot = foodNameSnapshot; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<OrderDetailOption> getOptions() { return options; }
    public void setOptions(List<OrderDetailOption> options) { this.options = options; }

    public void addOption(OrderDetailOption option) {
        this.options.add(option);
        option.setOrderDetail(this);
    }
}
