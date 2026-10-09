package com.foodordering.repository;

import com.foodordering.entity.Food;
import com.foodordering.enums.FoodStatus;

import java.util.List;
import java.util.Optional;

public interface FoodRepository {

    List<Food> findAll();

    List<Food> findAvailable();

    List<Food> findByCategoryId(String categoryId);

    List<Food> findAvailableByCategoryId(String categoryId);

    List<Food> search(String keyword, String categoryId, FoodStatus status);

    Optional<Food> findById(String id);

    Optional<Food> findByIdWithOptions(String id);

    boolean existsById(String id);

    long countByCategoryId(String categoryId);

    Food save(Food food);

    Food update(Food food);

    void deleteById(String id);

    String generateNextId();
}
