package com.foodordering.dao;

import com.foodordering.model.FoodOption;
import com.foodordering.enums.OptionType;

import java.util.List;
import java.util.Optional;

public interface FoodOptionDAO {

    List<FoodOption> findByFoodId(String foodId);

    List<FoodOption> findActiveByFoodId(String foodId);

    Optional<FoodOption> findById(String id);

    boolean existsById(String id);

    boolean existsByFoodIdAndTypeAndName(String foodId, OptionType type, String name);

    boolean existsByFoodIdAndTypeAndNameAndIdNot(String foodId, OptionType type, String name, String optionId);

    FoodOption save(FoodOption option);

    FoodOption update(FoodOption option);

    void deleteById(String id);

    String generateNextId();
}
