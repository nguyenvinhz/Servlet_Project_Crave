package com.foodordering.service;

import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.CreateCategoryRequest;
import com.foodordering.dto.CreateFoodOptionRequest;
import com.foodordering.dto.CreateFoodRequest;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodOptionResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.dto.UpdateCategoryRequest;
import com.foodordering.dto.UpdateFoodOptionRequest;
import com.foodordering.dto.UpdateFoodRequest;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.exception.BusinessRuleException;
import com.foodordering.exception.DuplicateResourceException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.mapper.MenuMapper;
import com.foodordering.repository.CategoryRepository;
import com.foodordering.repository.FoodOptionRepository;
import com.foodordering.repository.FoodRepository;
import com.foodordering.repository.JpaCategoryRepository;
import com.foodordering.repository.JpaFoodOptionRepository;
import com.foodordering.repository.JpaFoodRepository;
import com.foodordering.validator.MenuValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    // Category Operations
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

    public CategoryResponse createCategory(CreateCategoryRequest request) {
        MenuValidator.validateCreateCategory(request);
        String name = request.name().trim();
        if (categoryRepository.existsByName(name)) {
            throw new DuplicateResourceException("Tên danh mục '" + name + "' đã tồn tại.");
        }

        String nextId = categoryRepository.generateNextId();
        Category category = new Category(nextId, name, request.description() != null ? request.description().trim() : null);
        Category saved = categoryRepository.save(category);
        return MenuMapper.toCategoryResponse(saved, 0L);
    }

    public CategoryResponse updateCategory(String id, UpdateCategoryRequest request) {
        MenuValidator.validateUpdateCategory(request);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id));

        String name = request.name().trim();
        if (categoryRepository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateResourceException("Tên danh mục '" + name + "' đã tồn tại ở danh mục khác.");
        }

        category.setName(name);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        Category updated = categoryRepository.update(category);
        long count = foodRepository.countByCategoryId(id);
        return MenuMapper.toCategoryResponse(updated, count);
    }

    public void deleteCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id));

        long count = foodRepository.countByCategoryId(id);
        if (count > 0) {
            throw new BusinessRuleException("Không thể xóa danh mục đang có " + count + " món ăn. Hãy di chuyển hoặc xóa món trước.");
        }

        categoryRepository.deleteById(id);
    }

    // ==========================================
    // Food Operations
    // ==========================================

    public List<FoodSummaryResponse> getMenu(String categoryId, String keyword) {
        List<Food> foods = foodRepository.search(keyword, categoryId, FoodStatus.AVAILABLE);
        return foods.stream()
                .map(MenuMapper::toFoodSummaryResponse)
                .collect(Collectors.toList());
    }

    public List<FoodSummaryResponse> getAllFoodsAdmin(String categoryId, String keyword, FoodStatus status) {
        List<Food> foods = foodRepository.search(keyword, categoryId, status);
        return foods.stream()
                .map(MenuMapper::toFoodSummaryResponse)
                .collect(Collectors.toList());
    }

    public FoodDetailResponse getFoodDetail(String id, boolean onlyActiveOptions) {
        Food food = foodRepository.findByIdWithOptions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        return MenuMapper.toFoodDetailResponse(food, onlyActiveOptions);
    }

    public FoodDetailResponse createFood(CreateFoodRequest request) {
        MenuValidator.validateCreateFood(request);

        Category category = categoryRepository.findById(request.categoryId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + request.categoryId()));

        String nextId = foodRepository.generateNextId();
        FoodStatus status = request.status() != null ? request.status() : FoodStatus.AVAILABLE;
        Food food = new Food(
                nextId,
                category,
                request.name().trim(),
                request.price(),
                request.imageUrl() != null ? request.imageUrl().trim() : null,
                request.description() != null ? request.description().trim() : null,
                status
        );

        Food saved = foodRepository.save(food);
        return MenuMapper.toFoodDetailResponse(saved, false);
    }

    public FoodDetailResponse updateFood(String id, UpdateFoodRequest request) {
        MenuValidator.validateUpdateFood(request);

        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        Category category = categoryRepository.findById(request.categoryId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + request.categoryId()));

        food.setCategory(category);
        food.setName(request.name().trim());
        food.setPrice(request.price());
        food.setImageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null);
        food.setDescription(request.description() != null ? request.description().trim() : null);
        if (request.status() != null) {
            food.setStatus(request.status());
        }

        Food updated = foodRepository.update(food);
        return getFoodDetail(updated.getId(), false);
    }

    public void updateFoodStatus(String id, FoodStatus status) {
        if (status == null) {
            throw new BusinessRuleException("Trạng thái món không được để trống.");
        }
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));
        food.setStatus(status);
        foodRepository.update(food);
    }

    public void deleteFood(String id) {
        if (!foodRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id);
        }
        foodRepository.deleteById(id);
    }

    // ==========================================
    // Option Operations
    // ==========================================

    public FoodOptionResponse addFoodOption(String foodId, CreateFoodOptionRequest request) {
        MenuValidator.validateCreateOption(request);

        Food food = foodRepository.findById(foodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + foodId));

        String name = request.name().trim();
        if (foodOptionRepository.existsByFoodIdAndTypeAndName(foodId, request.optionType(), name)) {
            throw new DuplicateResourceException("Tùy chọn '" + name + "' thuộc nhóm " + request.optionType() + " đã tồn tại cho món này.");
        }

        String nextId = foodOptionRepository.generateNextId();
        BigDecimal extraPrice = request.extraPrice() != null ? request.extraPrice() : BigDecimal.ZERO;
        OptionStatus status = request.status() != null ? request.status() : OptionStatus.ACTIVE;

        FoodOption option = new FoodOption(nextId, food, request.optionType(), name, extraPrice, status);
        FoodOption saved = foodOptionRepository.save(option);
        return MenuMapper.toFoodOptionResponse(saved);
    }

    public FoodOptionResponse updateFoodOption(String optionId, UpdateFoodOptionRequest request) {
        MenuValidator.validateUpdateOption(request);

        FoodOption option = foodOptionRepository.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId));

        String name = request.name().trim();
        if (foodOptionRepository.existsByFoodIdAndTypeAndNameAndIdNot(option.getFood().getId(), request.optionType(), name, optionId)) {
            throw new DuplicateResourceException("Tùy chọn '" + name + "' thuộc nhóm " + request.optionType() + " đã tồn tại cho món này.");
        }

        option.setOptionType(request.optionType());
        option.setName(name);
        option.setExtraPrice(request.extraPrice() != null ? request.extraPrice() : BigDecimal.ZERO);
        if (request.status() != null) {
            option.setStatus(request.status());
        }

        FoodOption updated = foodOptionRepository.update(option);
        return MenuMapper.toFoodOptionResponse(updated);
    }

    public void updateOptionStatus(String optionId, OptionStatus status) {
        if (status == null) {
            throw new BusinessRuleException("Trạng thái tùy chọn không được để trống.");
        }
        FoodOption option = foodOptionRepository.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId));
        option.setStatus(status);
        foodOptionRepository.update(option);
    }

    public void deleteFoodOption(String optionId) {
        if (!foodOptionRepository.existsById(optionId)) {
            throw new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId);
        }
        foodOptionRepository.deleteById(optionId);
    }

    // ==========================================
    // Cross-Domain Validation (Menu - Cart consistency)
    // ==========================================

    public void validateFoodForOrder(String foodId, List<String> optionIds) {
        Food food = foodRepository.findByIdWithOptions(foodId)
                .orElseThrow(() -> new ResourceNotFoundException("Món ăn không tồn tại: " + foodId));

        if (food.getStatus() != FoodStatus.AVAILABLE) {
            throw new BusinessRuleException("Món '" + food.getName() + "' hiện tạm ngưng phục vụ.");
        }

        if (optionIds != null && !optionIds.isEmpty()) {
            List<FoodOption> foodOptions = food.getOptions() != null ? food.getOptions() : List.of();
            for (String optId : optionIds) {
                FoodOption option = foodOptions.stream()
                        .filter(o -> o.getId().equals(optId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException("Tùy chọn không thuộc món ăn này: " + optId));

                if (option.getStatus() != OptionStatus.ACTIVE) {
                    throw new BusinessRuleException("Tùy chọn '" + option.getName() + "' hiện không khả dụng.");
                }
            }
        }
    }
}
