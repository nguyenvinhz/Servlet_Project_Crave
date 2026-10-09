package com.foodordering.service;

import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.exception.ResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void getCategoriesReturnsAllCategoriesWithFoodCount() {
        Category c1 = new Category("DM01", "Burger", "Các loại burger");
        Category c2 = new Category("DM02", "Đồ uống", "Nước ngọt và trà");
        categoryRepo.save(c1);
        categoryRepo.save(c2);

        Food f1 = new Food("MA01", c1, "Burger bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE);
        Food f2 = new Food("MA02", c1, "Burger gà", BigDecimal.valueOf(45000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(f1);
        foodRepo.save(f2);

        List<CategoryResponse> categories = menuService.getCategories();

        assertEquals(2, categories.size());
        CategoryResponse burgerCat = categories.stream().filter(c -> c.id().equals("DM01")).findFirst().orElseThrow();
        assertEquals(2, burgerCat.foodCount());
        CategoryResponse drinkCat = categories.stream().filter(c -> c.id().equals("DM02")).findFirst().orElseThrow();
        assertEquals(0, drinkCat.foodCount());
    }

    @Test
    void getCategoryByIdReturnsCorrectCategory() {
        Category category = new Category("DM01", "Pizza", "Bánh pizza các loại");
        categoryRepo.save(category);

        CategoryResponse res = menuService.getCategoryById("DM01");

        assertNotNull(res);
        assertEquals("Pizza", res.name());
        assertEquals("DM01", res.id());
    }

    @Test
    void getCategoryByIdThrowsExceptionWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> menuService.getCategoryById("DM99"));
    }

    @Test
    void getMenuReturnsAvailableFoodsFiltered() {
        Category cat = new Category("DM01", "Món chính", "Món ăn");
        categoryRepo.save(cat);

        Food f1 = new Food("MA01", cat, "Cơm tấm sườn", BigDecimal.valueOf(40000), null, null, FoodStatus.AVAILABLE);
        Food f2 = new Food("MA02", cat, "Cơm tấm bì", BigDecimal.valueOf(35000), null, null, FoodStatus.UNAVAILABLE);
        foodRepo.save(f1);
        foodRepo.save(f2);

        List<FoodSummaryResponse> menu = menuService.getMenu("DM01", null);

        assertEquals(1, menu.size());
        assertEquals("Cơm tấm sườn", menu.get(0).name());
    }

    @Test
    void getFoodDetailReturnsFoodWithOptions() {
        Category cat = new Category("DM01", "Trà", "Các loại trà");
        categoryRepo.save(cat);

        Food food = new Food("MA01", cat, "Trà đào", BigDecimal.valueOf(30000), null, "Thơm ngon", FoodStatus.AVAILABLE);
        FoodOption opt1 = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        FoodOption opt2 = new FoodOption("TC02", food, OptionType.TOPPING, "Đào miếng", BigDecimal.valueOf(7000), OptionStatus.INACTIVE);
        food.addOption(opt1);
        food.addOption(opt2);
        foodRepo.save(food);

        FoodDetailResponse detail = menuService.getFoodDetail("MA01", true);

        assertEquals("Trà đào", detail.name());
        assertEquals(1, detail.options().size());
        assertEquals("Size L", detail.options().get(0).name());

        FoodDetailResponse detailAll = menuService.getFoodDetail("MA01", false);
        assertEquals(2, detailAll.options().size());
    }

    @Test
    void deleteCategoryRejectsIfFoodsExist() {
        Category cat = new Category("DM01", "Trà", "Các loại trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà đào", BigDecimal.valueOf(30000), null, "Thơm ngon", FoodStatus.AVAILABLE);
        foodRepo.save(food);

        assertThrows(IllegalStateException.class, () -> menuService.deleteCategory("DM01"));
    }

    @Test
    void deleteCategorySucceedsIfNoFoodsExist() {
        Category cat = new Category("DM01", "Trà", "Các loại trà");
        categoryRepo.save(cat);

        menuService.deleteCategory("DM01");
        assertFalse(categoryRepo.existsById("DM01"));
    }

    // =========================================================
    // Fake In-Memory Repositories for testing without database
    // =========================================================

    private static class FakeCategoryRepository implements CategoryRepository {
        private final Map<String, Category> data = new HashMap<>();

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
            return "DM01";
        }
    }

    private static class FakeFoodRepository implements FoodRepository {
        private final Map<String, Food> data = new HashMap<>();

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
            return "MA01";
        }
    }

    private static class FakeFoodOptionRepository implements FoodOptionRepository {
        private final Map<String, FoodOption> data = new HashMap<>();

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
            return false;
        }

        @Override
        public boolean existsByFoodIdAndTypeAndNameAndIdNot(String foodId, OptionType type, String name, String optionId) {
            return false;
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
            return "TC01";
        }
    }
}
