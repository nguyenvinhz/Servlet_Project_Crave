package com.foodordering.service;

import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.enums.FoodStatus;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.mapper.MenuMapper;
import com.foodordering.repository.CategoryRepository;
import com.foodordering.repository.FoodOptionRepository;
import com.foodordering.repository.FoodRepository;
import com.foodordering.repository.JpaCategoryRepository;
import com.foodordering.repository.JpaFoodOptionRepository;
import com.foodordering.repository.JpaFoodRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service nền tảng cho Menu và Danh mục món ăn (Ngày 1: Chốt hợp đồng & đọc dữ liệu mẫu).
 */
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final FoodRepository foodRepository;
    private final FoodOptionRepository foodOptionRepository;

    public MenuService() {
        this(new JpaCategoryRepository(), new JpaFoodRepository(), new JpaFoodOptionRepository());
    }

    public MenuService(CategoryRepository categoryRepository,
                       FoodRepository foodRepository,
                       FoodOptionRepository foodOptionRepository) {
        this.categoryRepository = categoryRepository;
        this.foodRepository = foodRepository;
        this.foodOptionRepository = foodOptionRepository;
    }

    // ==========================================
    // Category Operations (Read)
    // ==========================================

    public List<CategoryResponse> getCategories() {
        List<Category> categories = categoryRepository.findAll();
        List<CategoryResponse> responses = new ArrayList<>();
        for (Category category : categories) {
            long count = foodRepository.countByCategoryId(category.getId());
            responses.add(MenuMapper.toCategoryResponse(category, count));
        }
        return responses;
    }

    public CategoryResponse getCategoryById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id));
        long count = foodRepository.countByCategoryId(category.getId());
        return MenuMapper.toCategoryResponse(category, count);
    }

    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id);
        }
        if (foodRepository.countByCategoryId(id) > 0) {
            throw new IllegalStateException("Không thể xóa danh mục đang có món ăn.");
        }
        categoryRepository.deleteById(id);
    }

    // ==========================================
    // Food Operations (Read)
    // ==========================================

    public List<FoodSummaryResponse> getMenu(String categoryId, String keyword) {
        List<Food> foods = foodRepository.search(keyword, categoryId, FoodStatus.AVAILABLE);
        return foods.stream()
                .map(MenuMapper::toFoodSummaryResponse)
                .collect(Collectors.toList());
    }

    public FoodDetailResponse getFoodDetail(String id, boolean onlyActiveOptions) {
        Food food = foodRepository.findByIdWithOptions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        return MenuMapper.toFoodDetailResponse(food, onlyActiveOptions);
    }
}
