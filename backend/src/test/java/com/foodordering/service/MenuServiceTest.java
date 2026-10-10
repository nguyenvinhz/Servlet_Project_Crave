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
import com.foodordering.enums.OptionType;
import com.foodordering.exception.ConflictException;
import com.foodordering.exception.ResourceNotFoundException;
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

    // ==========================================
    // Category Tests
    // ==========================================

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
    void createCategorySucceedsWithValidData() {
        CategoryRequest req = new CategoryRequest("Món chay", "Thanh đạm");
        CategoryResponse res = menuService.createCategory(req);

        assertNotNull(res);
        assertEquals("Món chay", res.name());
        assertEquals("Thanh đạm", res.description());
        assertTrue(categoryRepo.existsByName("Món chay"));
    }

    @Test
    void createCategoryRejectsDuplicateName() {
        categoryRepo.save(new Category("DM01", "Món chay", "Thanh đạm"));
        CategoryRequest req = new CategoryRequest("Món chay", "Khác");

        assertThrows(ConflictException.class, () -> menuService.createCategory(req));
    }

    @Test
    void createCategoryRejectsEmptyName() {
        CategoryRequest req = new CategoryRequest("", "Mô tả");
        assertThrows(ValidationException.class, () -> menuService.createCategory(req));
    }

    @Test
    void updateCategorySucceeds() {
        categoryRepo.save(new Category("DM01", "Trà", "Các loại trà"));
        CategoryRequest req = new CategoryRequest("Trà & Cà phê", "Đồ uống thơm ngon");

        CategoryResponse updated = menuService.updateCategory("DM01", req);
        assertEquals("Trà & Cà phê", updated.name());
        assertEquals("Đồ uống thơm ngon", updated.description());
    }

    @Test
    void updateCategoryRejectsDuplicateNameOfOtherCategory() {
        categoryRepo.save(new Category("DM01", "Trà", "Mô tả 1"));
        categoryRepo.save(new Category("DM02", "Cà phê", "Mô tả 2"));

        CategoryRequest req = new CategoryRequest("Cà phê", "Đổi tên trùng");
        assertThrows(ConflictException.class, () -> menuService.updateCategory("DM01", req));
    }

    @Test
    void deleteCategoryRejectsIfFoodsExist() {
        Category cat = new Category("DM01", "Trà", "Các loại trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà đào", BigDecimal.valueOf(30000), null, "Thơm ngon", FoodStatus.AVAILABLE);
        foodRepo.save(food);

        assertThrows(ConflictException.class, () -> menuService.deleteCategory("DM01"));
    }

    @Test
    void deleteCategorySucceedsIfNoFoodsExist() {
        Category cat = new Category("DM01", "Trà", "Các loại trà");
        categoryRepo.save(cat);

        menuService.deleteCategory("DM01");
        assertFalse(categoryRepo.existsById("DM01"));
    }

    // ==========================================
    // Food Tests
    // ==========================================

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
    void getAllFoodsForAdminReturnsAllStatuses() {
        Category cat = new Category("DM01", "Món chính", "Món ăn");
        categoryRepo.save(cat);

        Food f1 = new Food("MA01", cat, "Cơm sườn", BigDecimal.valueOf(40000), null, null, FoodStatus.AVAILABLE);
        Food f2 = new Food("MA02", cat, "Cơm bì", BigDecimal.valueOf(35000), null, null, FoodStatus.UNAVAILABLE);
        foodRepo.save(f1);
        foodRepo.save(f2);

        List<FoodSummaryResponse> allFoods = menuService.getAllFoodsForAdmin("DM01", null, null);
        assertEquals(2, allFoods.size());
    }

    @Test
    void getFoodDetailReturnsFoodWithOptions() {
        Category cat = new Category("DM01", "Trà sữa", "Thức uống");
        categoryRepo.save(cat);

        Food food = new Food("MA01", cat, "Trà sữa Ô Long", BigDecimal.valueOf(30000), null, "Trà thơm ngon", FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodOption opt1 = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        FoodOption opt2 = new FoodOption("TC02", food, OptionType.TOPPING, "Trân châu đen", BigDecimal.valueOf(5000), OptionStatus.INACTIVE);
        food.addOption(opt1);
        food.addOption(opt2);
        optionRepo.save(opt1);
        optionRepo.save(opt2);

        FoodDetailResponse detailActiveOnly = menuService.getFoodDetail("MA01", true);
        assertEquals(1, detailActiveOnly.options().size());
        assertEquals("Size L", detailActiveOnly.options().get(0).name());

        FoodDetailResponse detailAll = menuService.getFoodDetail("MA01", false);
        assertEquals(2, detailAll.options().size());
    }

    @Test
    void createFoodSucceeds() {
        Category cat = new Category("DM01", "Món chính", "Món");
        categoryRepo.save(cat);

        FoodRequest req = new FoodRequest("DM01", "Phở bò", BigDecimal.valueOf(50000), "http://img.com/pho.jpg", "Nước dùng ngọt thanh", FoodStatus.AVAILABLE);
        FoodDetailResponse created = menuService.createFood(req);

        assertNotNull(created);
        assertEquals("Phở bò", created.name());
        assertEquals(BigDecimal.valueOf(50000), created.price());
        assertEquals("Món chính", created.categoryName());
    }

    @Test
    void createFoodRejectsNegativePrice() {
        Category cat = new Category("DM01", "Món chính", "Món");
        categoryRepo.save(cat);

        FoodRequest req = new FoodRequest("DM01", "Phở bò", BigDecimal.valueOf(-1000), null, null, FoodStatus.AVAILABLE);
        assertThrows(ValidationException.class, () -> menuService.createFood(req));
    }

    @Test
    void createFoodRejectsNonExistentCategory() {
        FoodRequest req = new FoodRequest("DM99", "Phở bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE);
        assertThrows(ResourceNotFoundException.class, () -> menuService.createFood(req));
    }

    @Test
    void updateFoodSucceeds() {
        Category cat = new Category("DM01", "Món chính", "Món");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Phở bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodRequest req = new FoodRequest("DM01", "Phở bò đặc biệt", BigDecimal.valueOf(65000), null, "Nhiều thịt", FoodStatus.AVAILABLE);
        FoodDetailResponse updated = menuService.updateFood("MA01", req);

        assertEquals("Phở bò đặc biệt", updated.name());
        assertEquals(BigDecimal.valueOf(65000), updated.price());
    }

    @Test
    void updateFoodStatusSucceeds() {
        Category cat = new Category("DM01", "Món chính", "Món");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Phở bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodDetailResponse updated = menuService.updateFoodStatus("MA01", FoodStatus.UNAVAILABLE);
        assertEquals(FoodStatus.UNAVAILABLE, updated.status());
    }

    @Test
    void deleteFoodSucceeds() {
        Category cat = new Category("DM01", "Món chính", "Món");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Phở bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        menuService.deleteFood("MA01");
        assertFalse(foodRepo.existsById("MA01"));
    }

    // ==========================================
    // Food Option Tests
    // ==========================================

    @Test
    void createFoodOptionSucceeds() {
        Category cat = new Category("DM01", "Nước", "Nước");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodOptionRequest req = new FoodOptionRequest("MA01", OptionType.SIZE, "Size XL", BigDecimal.valueOf(10000), OptionStatus.ACTIVE);
        FoodOptionResponse res = menuService.createFoodOption("MA01", req);

        assertNotNull(res);
        assertEquals("Size XL", res.name());
        assertEquals(OptionType.SIZE, res.optionType());
        assertEquals(BigDecimal.valueOf(10000), res.extraPrice());
    }

    @Test
    void createFoodOptionRejectsDuplicate() {
        Category cat = new Category("DM01", "Nước", "Nước");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);
        FoodOption opt = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        optionRepo.save(opt);

        FoodOptionRequest req = new FoodOptionRequest("MA01", OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        assertThrows(ConflictException.class, () -> menuService.createFoodOption("MA01", req));
    }

    @Test
    void updateFoodOptionSucceeds() {
        Category cat = new Category("DM01", "Nước", "Nước");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);
        FoodOption opt = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        optionRepo.save(opt);

        FoodOptionRequest req = new FoodOptionRequest("MA01", OptionType.SIZE, "Size L (Cỡ Lớn)", BigDecimal.valueOf(7000), OptionStatus.ACTIVE);
        FoodOptionResponse updated = menuService.updateFoodOption("TC01", req);

        assertEquals("Size L (Cỡ Lớn)", updated.name());
        assertEquals(BigDecimal.valueOf(7000), updated.extraPrice());
    }

    @Test
    void deleteFoodOptionSucceeds() {
        Category cat = new Category("DM01", "Nước", "Nước");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);
        FoodOption opt = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        optionRepo.save(opt);

        menuService.deleteFoodOption("TC01");
        assertFalse(optionRepo.existsById("TC01"));
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
            return "DM" + (data.size() + 1);
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
            return "MA" + (data.size() + 1);
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
            return data.values().stream().anyMatch(o -> o.getFood().getId().equals(foodId)
                    && o.getOptionType() == type
                    && o.getName().equalsIgnoreCase(name));
        }

        @Override
        public boolean existsByFoodIdAndTypeAndNameAndIdNot(String foodId, OptionType type, String name, String optionId) {
            return data.values().stream().anyMatch(o -> o.getFood().getId().equals(foodId)
                    && o.getOptionType() == type
                    && o.getName().equalsIgnoreCase(name)
                    && !o.getId().equals(optionId));
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
            return "TC" + (data.size() + 1);
        }
    }
}
