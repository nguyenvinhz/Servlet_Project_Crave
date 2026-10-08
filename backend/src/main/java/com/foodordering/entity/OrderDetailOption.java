package com.foodordering.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_detail_option")
public class OrderDetailOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_detail_option_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_detail_id", nullable = false)
    private OrderDetail orderDetail;

    @Column(name = "option_id", length = 10)
    private String optionId;

    @Column(name = "option_name_snapshot", length = 100, nullable = false)
    private String optionNameSnapshot;

    @Column(name = "extra_price_snapshot", nullable = false)
    private BigDecimal extraPriceSnapshot;

    public OrderDetailOption() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OrderDetail getOrderDetail() { return orderDetail; }
    public void setOrderDetail(OrderDetail orderDetail) { this.orderDetail = orderDetail; }
    public String getOptionId() { return optionId; }
    public void setOptionId(String optionId) { this.optionId = optionId; }
    public String getOptionNameSnapshot() { return optionNameSnapshot; }
    public void setOptionNameSnapshot(String optionNameSnapshot) { this.optionNameSnapshot = optionNameSnapshot; }
    public BigDecimal getExtraPriceSnapshot() { return extraPriceSnapshot; }
    public void setExtraPriceSnapshot(BigDecimal extraPriceSnapshot) { this.extraPriceSnapshot = extraPriceSnapshot; }
}
