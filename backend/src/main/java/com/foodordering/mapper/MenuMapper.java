package com.foodordering.mapper;

import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodOptionResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.OptionStatus;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class MenuMapper {

    private MenuMapper() {
    }

    public static CategoryResponse toCategoryResponse(Category category, long foodCount) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                foodCount
        );
    }

    public static FoodOptionResponse toFoodOptionResponse(FoodOption option) {
        if (option == null) {
            return null;
        }
        return new FoodOptionResponse(
                option.getId(),
                option.getFood() != null ? option.getFood().getId() : null,
                option.getOptionType(),
                option.getName(),
                option.getExtraPrice(),
                option.getStatus()
        );
    }

    public static FoodSummaryResponse toFoodSummaryResponse(Food food) {
        if (food == null) {
            return null;
        }
        return new FoodSummaryResponse(
                food.getId(),
                food.getCategory() != null ? food.getCategory().getId() : null,
                food.getCategory() != null ? food.getCategory().getName() : null,
                food.getName(),
                food.getPrice(),
                food.getImageUrl(),
                food.getDescription(),
                food.getStatus()
        );
    }

    public static FoodDetailResponse toFoodDetailResponse(Food food, boolean onlyActiveOptions) {
        if (food == null) {
            return null;
        }
        List<FoodOptionResponse> options;
        if (food.getOptions() == null) {
            options = Collections.emptyList();
        } else {
            options = food.getOptions().stream()
                    .filter(opt -> !onlyActiveOptions || opt.getStatus() == OptionStatus.ACTIVE)
                    .map(MenuMapper::toFoodOptionResponse)
                    .collect(Collectors.toList());
        }

        return new FoodDetailResponse(
                food.getId(),
                food.getCategory() != null ? food.getCategory().getId() : null,
                food.getCategory() != null ? food.getCategory().getName() : null,
                food.getName(),
                food.getPrice(),
                food.getImageUrl(),
                food.getDescription(),
                food.getStatus(),
                options
        );
    }
}
