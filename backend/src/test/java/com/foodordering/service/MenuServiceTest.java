package com.foodordering.service;

import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.CreateCategoryRequest;
import com.foodordering.dto.CreateFoodRequest;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.exception.BusinessRuleException;
import com.foodordering.exception.DuplicateResourceException;
import com.foodordering.exception.ValidationException;
import com.foodordering.repository.CategoryRepository;
import com.foodordering.repository.FoodOptionRepository;
import com.foodordering.repository.FoodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuServiceTest {

    private FakeCategoryRepository categoryRepo;
    private FakeFoodRepository foodRepo;
    private FakeFoodOptionRepository optionRepo;
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        categoryRepo = new FakeCategoryRepository();
        foodRepo = new FakeFoodRepository();
        optionRepo = new FakeFoodOptionRepository();
        menuService = new MenuService(categoryRepo, foodRepo, optionRepo);
    }

    @Test
    void createCategorySuccessfullyWhenDataIsValid() {
        CreateCategoryRequest request = new CreateCategoryRequest("Món mới", "Mô tả món mới");
        CategoryResponse response = menuService.createCategory(request);

        assertNotNull(response);
        assertEquals("DM01", response.id());
        assertEquals("Món mới", response.name());
        assertEquals("Mô tả món mới", response.description());
    }

    @Test
    void createCategoryThrowsDuplicateWhenNameExists() {
        categoryRepo.save(new Category("DM01", "Món lẩu", "Nồi lẩu nóng"));

        CreateCategoryRequest request = new CreateCategoryRequest("Món lẩu", "Trùng tên");
        assertThrows(DuplicateResourceException.class, () -> menuService.createCategory(request));
    }

    @Test
    void deleteCategoryThrowsBusinessRuleExceptionWhenCategoryContainsFoods() {
        Category category = new Category("DM01", "Cơm", "Các loại cơm");
        categoryRepo.save(category);

        Food food = new Food("MA01", category, "Cơm sườn", BigDecimal.valueOf(35000), null, null);
        foodRepo.save(food);

        assertThrows(BusinessRuleException.class, () -> menuService.deleteCategory("DM01"));
    }

    @Test
    void createFoodThrowsValidationExceptionWhenPriceIsZeroOrNegative() {
        Category category = new Category("DM01", "Cơm", "Các loại cơm");
        categoryRepo.save(category);

        CreateFoodRequest zeroPriceReq = new CreateFoodRequest(
                "DM01", "Cơm gà", BigDecimal.ZERO, null, null, FoodStatus.AVAILABLE);

        assertThrows(ValidationException.class, () -> menuService.createFood(zeroPriceReq));
    }

    @Test
    void validateFoodForOrderThrowsExceptionWhenFoodUnavailable() {
        Category category = new Category("DM01", "Trà", "Các loại trà");
        categoryRepo.save(category);

        Food food = new Food("MA01", category, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.UNAVAILABLE);
        foodRepo.save(food);

        assertThrows(BusinessRuleException.class, () -> menuService.validateFoodForOrder("MA01", null));
    }

    @Test
    void validateFoodForOrderThrowsExceptionWhenOptionInactive() {
        Category category = new Category("DM01", "Trà", "Các loại trà");
        categoryRepo.save(category);

        Food food = new Food("MA01", category, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        FoodOption option = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.INACTIVE);
        food.addOption(option);
        foodRepo.save(food);
        optionRepo.save(option);

        assertThrows(BusinessRuleException.class, () -> menuService.validateFoodForOrder("MA01", List.of("TC01")));
    }

    // =========================================================
    // Fake In-Memory Repositories for testing without database
    // =========================================================

    private static class FakeCategoryRepository implements CategoryRepository {
        private final Map<String, Category> data = new HashMap<>();
        private int sequence = 1;

        @Override
        public List<Category> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public Optional<Category> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public Optional<Category> findByName(String name) {
            return data.values().stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst();
        }

        @Override
        public boolean existsById(String id) {
            return data.containsKey(id);
        }

        @Override
        public boolean existsByName(String name) {
            return data.values().stream().anyMatch(c -> c.getName().equalsIgnoreCase(name));
        }

        @Override
        public boolean existsByNameAndIdNot(String name, String id) {
            return data.values().stream().anyMatch(c -> c.getName().equalsIgnoreCase(name) && !c.getId().equals(id));
        }

        @Override
        public Category save(Category category) {
            data.put(category.getId(), category);
            return category;
        }

        @Override
        public Category update(Category category) {
            data.put(category.getId(), category);
            return category;
        }

        @Override
        public void deleteById(String id) {
            data.remove(id);
        }

        @Override
        public String generateNextId() {
            return String.format("DM%02d", sequence++);
        }
    }

    private static class FakeFoodRepository implements FoodRepository {
        private final Map<String, Food> data = new HashMap<>();
        private int sequence = 1;

        @Override
        public List<Food> findAll() {
            return new ArrayList<>(data.values());
        }

        @Override
        public List<Food> findAvailable() {
            return data.values().stream().filter(f -> f.getStatus() == FoodStatus.AVAILABLE).toList();
        }

        @Override
        public List<Food> findByCategoryId(String categoryId) {
            return data.values().stream().filter(f -> f.getCategory().getId().equals(categoryId)).toList();
        }

        @Override
        public List<Food> findAvailableByCategoryId(String categoryId) {
            return data.values().stream().filter(f -> f.getCategory().getId().equals(categoryId) && f.getStatus() == FoodStatus.AVAILABLE).toList();
        }

        @Override
        public List<Food> search(String keyword, String categoryId, FoodStatus status) {
            return data.values().stream()
                    .filter(f -> categoryId == null || f.getCategory().getId().equals(categoryId))
                    .filter(f -> status == null || f.getStatus() == status)
                    .filter(f -> keyword == null || f.getName().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
        }

        @Override
        public Optional<Food> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public Optional<Food> findByIdWithOptions(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public boolean existsById(String id) {
            return data.containsKey(id);
        }

        @Override
        public long countByCategoryId(String categoryId) {
            return data.values().stream().filter(f -> f.getCategory().getId().equals(categoryId)).count();
        }

        @Override
        public Food save(Food food) {
            data.put(food.getId(), food);
            return food;
        }

        @Override
        public Food update(Food food) {
            data.put(food.getId(), food);
            return food;
        }

        @Override
        public void deleteById(String id) {
            data.remove(id);
        }

        @Override
        public String generateNextId() {
            return String.format("MA%02d", sequence++);
        }
    }

    private static class FakeFoodOptionRepository implements FoodOptionRepository {
        private final Map<String, FoodOption> data = new HashMap<>();
        private int sequence = 1;

        @Override
        public List<FoodOption> findByFoodId(String foodId) {
            return data.values().stream().filter(o -> o.getFood().getId().equals(foodId)).toList();
        }

        @Override
        public List<FoodOption> findActiveByFoodId(String foodId) {
            return data.values().stream().filter(o -> o.getFood().getId().equals(foodId) && o.getStatus() == OptionStatus.ACTIVE).toList();
        }

        @Override
        public Optional<FoodOption> findById(String id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public boolean existsById(String id) {
            return data.containsKey(id);
        }

        @Override
        public boolean existsByFoodIdAndTypeAndName(String foodId, OptionType type, String name) {
            return data.values().stream().anyMatch(o -> o.getFood().getId().equals(foodId) && o.getOptionType() == type && o.getName().equalsIgnoreCase(name));
        }

        @Override
        public boolean existsByFoodIdAndTypeAndNameAndIdNot(String foodId, OptionType type, String name, String optionId) {
            return data.values().stream().anyMatch(o -> o.getFood().getId().equals(foodId) && o.getOptionType() == type && o.getName().equalsIgnoreCase(name) && !o.getId().equals(optionId));
        }

        @Override
        public FoodOption save(FoodOption option) {
            data.put(option.getId(), option);
            return option;
        }

        @Override
        public FoodOption update(FoodOption option) {
            data.put(option.getId(), option);
            return option;
        }

        @Override
        public void deleteById(String id) {
            data.remove(id);
        }

        @Override
        public String generateNextId() {
            return String.format("TC%02d", sequence++);
        }
    }
}
