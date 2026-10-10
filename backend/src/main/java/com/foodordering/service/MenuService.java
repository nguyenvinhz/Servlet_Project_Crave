package com.foodordering.service;

import com.foodordering.dto.CategoryRequest;
import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodOptionRequest;
import com.foodordering.dto.FoodOptionResponse;
import com.foodordering.dto.FoodRequest;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.exception.ConflictException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.exception.ValidationException;
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

/**
 * Service nghiệp vụ Menu, Quản lý món ăn, Danh mục và Tùy chọn món (Ngày 2).
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

    public CategoryResponse createCategory(CategoryRequest request) {
        MenuValidator.validateCategory(request);
        String trimmedName = request.name().trim();
        if (categoryRepository.existsByName(trimmedName)) {
            throw new ConflictException("Tên danh mục '" + trimmedName + "' đã tồn tại.");
        }

        String id = categoryRepository.generateNextId();
        String description = request.description() != null ? request.description().trim() : null;
        Category category = new Category(id, trimmedName, description);
        Category saved = categoryRepository.save(category);
        return MenuMapper.toCategoryResponse(saved, 0L);
    }

    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        MenuValidator.validateCategory(request);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id));

        String trimmedName = request.name().trim();
        if (categoryRepository.existsByNameAndIdNot(trimmedName, id)) {
            throw new ConflictException("Tên danh mục '" + trimmedName + "' đã tồn tại.");
        }

        category.setName(trimmedName);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        Category updated = categoryRepository.update(category);
        long count = foodRepository.countByCategoryId(id);
        return MenuMapper.toCategoryResponse(updated, count);
    }

    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id);
        }
        if (foodRepository.countByCategoryId(id) > 0) {
            throw new ConflictException("Không thể xóa danh mục đang có món ăn.");
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

    public List<FoodSummaryResponse> getAllFoodsForAdmin(String categoryId, String keyword, FoodStatus status) {
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

    public FoodDetailResponse createFood(FoodRequest request) {
        MenuValidator.validateFood(request);
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + request.categoryId()));

        String id = foodRepository.generateNextId();
        FoodStatus status = request.status() != null ? request.status() : FoodStatus.AVAILABLE;
        String imageUrl = request.imageUrl() != null ? request.imageUrl().trim() : null;
        String description = request.description() != null ? request.description().trim() : null;

        Food food = new Food(id, category, request.name().trim(), request.price(), imageUrl, description, status);
        Food saved = foodRepository.save(food);
        return MenuMapper.toFoodDetailResponse(saved, false);
    }

    public FoodDetailResponse updateFood(String id, FoodRequest request) {
        MenuValidator.validateFood(request);
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + request.categoryId()));

        food.setCategory(category);
        food.setName(request.name().trim());
        food.setPrice(request.price());
        food.setImageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null);
        food.setDescription(request.description() != null ? request.description().trim() : null);
        if (request.status() != null) {
            food.setStatus(request.status());
        }

        foodRepository.update(food);
        Food reloaded = foodRepository.findByIdWithOptions(id).orElse(food);
        return MenuMapper.toFoodDetailResponse(reloaded, false);
    }

    public FoodDetailResponse updateFoodStatus(String id, FoodStatus newStatus) {
        if (newStatus == null) {
            throw new ValidationException("Trạng thái món ăn không được để trống.");
        }
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        food.setStatus(newStatus);
        foodRepository.update(food);
        Food reloaded = foodRepository.findByIdWithOptions(id).orElse(food);
        return MenuMapper.toFoodDetailResponse(reloaded, false);
    }

    public void deleteFood(String id) {
        if (!foodRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id);
        }
        foodRepository.deleteById(id);
    }

    // ==========================================
    // Food Option Operations
    // ==========================================

    public List<FoodOptionResponse> getOptionsByFoodId(String foodId, boolean onlyActive) {
        if (!foodRepository.existsById(foodId)) {
            throw new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + foodId);
        }
        List<FoodOption> options = onlyActive
                ? foodOptionRepository.findActiveByFoodId(foodId)
                : foodOptionRepository.findByFoodId(foodId);

        return options.stream()
                .map(MenuMapper::toFoodOptionResponse)
                .collect(Collectors.toList());
    }

    public FoodOptionResponse getFoodOptionById(String optionId) {
        FoodOption option = foodOptionRepository.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId));
        return MenuMapper.toFoodOptionResponse(option);
    }

    public FoodOptionResponse createFoodOption(String foodId, FoodOptionRequest request) {
        MenuValidator.validateFoodOption(request);
        Food food = foodRepository.findById(foodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + foodId));

        String trimmedName = request.name().trim();
        if (foodOptionRepository.existsByFoodIdAndTypeAndName(foodId, request.optionType(), trimmedName)) {
            throw new ConflictException("Tùy chọn '" + trimmedName + "' thuộc nhóm " + request.optionType() + " đã tồn tại cho món này.");
        }

        String id = foodOptionRepository.generateNextId();
        BigDecimal extraPrice = request.extraPrice() != null ? request.extraPrice() : BigDecimal.ZERO;
        OptionStatus status = request.status() != null ? request.status() : OptionStatus.ACTIVE;

        FoodOption option = new FoodOption(id, food, request.optionType(), trimmedName, extraPrice, status);
        FoodOption saved = foodOptionRepository.save(option);
        return MenuMapper.toFoodOptionResponse(saved);
    }

    public FoodOptionResponse updateFoodOption(String optionId, FoodOptionRequest request) {
        MenuValidator.validateFoodOption(request);
        FoodOption option = foodOptionRepository.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId));

        String foodId = option.getFood().getId();
        String trimmedName = request.name().trim();
        if (foodOptionRepository.existsByFoodIdAndTypeAndNameAndIdNot(foodId, request.optionType(), trimmedName, optionId)) {
            throw new ConflictException("Tùy chọn '" + trimmedName + "' thuộc nhóm " + request.optionType() + " đã tồn tại cho món này.");
        }

        option.setOptionType(request.optionType());
        option.setName(trimmedName);
        option.setExtraPrice(request.extraPrice() != null ? request.extraPrice() : BigDecimal.ZERO);
        if (request.status() != null) {
            option.setStatus(request.status());
        }

        FoodOption updated = foodOptionRepository.update(option);
        return MenuMapper.toFoodOptionResponse(updated);
    }

    public void deleteFoodOption(String optionId) {
        if (!foodOptionRepository.existsById(optionId)) {
            throw new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId);
        }
        foodOptionRepository.deleteById(optionId);
    }
}
