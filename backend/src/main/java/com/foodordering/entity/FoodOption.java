package com.foodordering.entity;

import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "food_option")
public class FoodOption {

    @Id
    @Column(name = "option_id", length = 10, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false)
    private OptionType optionType;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "extra_price", precision = 12, scale = 0, nullable = false)
    private BigDecimal extraPrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OptionStatus status = OptionStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected FoodOption() {
    }

    public FoodOption(String id, Food food, OptionType optionType, String name, BigDecimal extraPrice) {
        this.id = id;
        this.food = food;
        this.optionType = optionType;
        this.name = name;
        this.extraPrice = extraPrice != null ? extraPrice : BigDecimal.ZERO;
        this.status = OptionStatus.ACTIVE;
    }

    public FoodOption(String id, Food food, OptionType optionType, String name, BigDecimal extraPrice, OptionStatus status) {
        this(id, food, optionType, name, extraPrice);
        this.status = status != null ? status : OptionStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Food getFood() {
        return food;
    }

    public void setFood(Food food) {
        this.food = food;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(OptionType optionType) {
        this.optionType = optionType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getExtraPrice() {
        return extraPrice;
    }

    public void setExtraPrice(BigDecimal extraPrice) {
        this.extraPrice = extraPrice != null ? extraPrice : BigDecimal.ZERO;
    }

    public OptionStatus getStatus() {
        return status;
    }

    public void setStatus(OptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
