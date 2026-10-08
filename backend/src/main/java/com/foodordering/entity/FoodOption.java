package com.foodordering.entity;

import com.foodordering.enums.FoodOptionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "food_option", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"food_id", "option_type", "name"})
})
public class FoodOption {

    @Id
    @Column(name = "option_id", length = 10, nullable = false)
    private String optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false)
    private FoodOptionType optionType;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "extra_price", nullable = false)
    private BigDecimal extraPrice = BigDecimal.ZERO;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public FoodOption() {
    }

    public FoodOption(String optionId) {
        this.optionId = optionId;
    }

    public FoodOption(String optionId, String name, FoodOptionType optionType, BigDecimal extraPrice) {
        this.optionId = optionId;
        this.name = name;
        this.optionType = optionType;
        this.extraPrice = extraPrice;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public Food getFood() {
        return food;
    }

    public void setFood(Food food) {
        this.food = food;
    }

    public FoodOptionType getOptionType() {
        return optionType;
    }

    public void setOptionType(FoodOptionType optionType) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
