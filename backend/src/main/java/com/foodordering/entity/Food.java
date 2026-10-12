package com.foodordering.entity;

import com.foodordering.enums.FoodStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "food")
public class Food {

    @Id
    @Column(name = "food_id", length = 10, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "price", precision = 12, scale = 0, nullable = false)
    private BigDecimal price;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FoodStatus status = FoodStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "food", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FoodOption> options = new ArrayList<>();

    public Food() {
    }

    public Food(String id) {
        this.id = id;
    }

    public Food(String id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Food(String id, Category category, String name, BigDecimal price, String imageUrl, String description) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.description = description;
        this.status = FoodStatus.AVAILABLE;
    }

    public Food(String id, Category category, String name, BigDecimal price, String imageUrl, String description,
                FoodStatus status) {
        this(id, category, name, price, imageUrl, description);
        this.status = status != null ? status : FoodStatus.AVAILABLE;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFoodId() {
        return id;
    }

    public void setFoodId(String foodId) {
        this.id = foodId;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getBasePrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FoodStatus getStatus() {
        return status;
    }

    public void setStatus(FoodStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<FoodOption> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public void setOptions(List<FoodOption> options) {
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
    }

    public void addOption(FoodOption option) {
        options.add(option);
        option.setFood(this);
    }

    public void removeOption(FoodOption option) {
        options.remove(option);
        option.setFood(null);
    }
}
