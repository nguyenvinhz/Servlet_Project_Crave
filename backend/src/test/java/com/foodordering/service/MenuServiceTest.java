package com.foodordering.service;

import com.foodordering.dto.CartConsistencyItemRequest;
import com.foodordering.dto.CartConsistencyItemResult;
import com.foodordering.dto.CartConsistencyReport;
import com.foodordering.dto.CategoryRequest;
import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodOptionRequest;
import com.foodordering.dto.FoodOptionResponse;
import com.foodordering.dto.FoodOptionValidationResult;
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
import com.foodordering.dao.CategoryDAO;
import com.foodordering.dao.FoodOptionDAO;
import com.foodordering.dao.FoodDAO;
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

    // ==========================================
    // Day 3 Tests: Unavailable Foods, Option Validation, Menu-Cart Consistency
    // ==========================================

    @Test
    void validateFoodAvailableSucceedsWhenFoodIsAvailable() {
        Category cat = new Category("DM01", "Cơm", "Cơm");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Cơm sườn", BigDecimal.valueOf(35000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        Food result = menuService.validateFoodAvailable("MA01");
        assertNotNull(result);
        assertEquals("MA01", result.getId());
    }

    @Test
    void validateFoodAvailableThrowsValidationExceptionWhenFoodIsUnavailable() {
        Category cat = new Category("DM01", "Cơm", "Cơm");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Cơm sườn", BigDecimal.valueOf(35000), null, null, FoodStatus.UNAVAILABLE);
        foodRepo.save(food);

        ValidationException ex = assertThrows(ValidationException.class, () -> menuService.validateFoodAvailable("MA01"));
        assertTrue(ex.getMessage().contains("tạm ngưng phục vụ"));
    }

    @Test
    void validateFoodAvailableThrowsResourceNotFoundExceptionWhenFoodNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> menuService.validateFoodAvailable("UNKNOWN_MA"));
    }

    @Test
    void validateFoodAvailableThrowsValidationExceptionWhenIdIsBlank() {
        assertThrows(ValidationException.class, () -> menuService.validateFoodAvailable("   "));
    }

    @Test
    void validateFoodOptionsSucceedsWithActiveOptionsAndComputesUnitPrice() {
        Category cat = new Category("DM01", "Trà", "Trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà đào", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodOption opt1 = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        FoodOption opt2 = new FoodOption("TC02", food, OptionType.TOPPING, "Đào thêm", BigDecimal.valueOf(8000), OptionStatus.ACTIVE);
        FoodOption opt3 = new FoodOption("TC03", food, OptionType.SUGAR_LEVEL, "50% đường", BigDecimal.ZERO, OptionStatus.ACTIVE);
        optionRepo.save(opt1);
        optionRepo.save(opt2);
        optionRepo.save(opt3);

        FoodOptionValidationResult res = menuService.validateFoodOptions("MA01", List.of("TC01", "TC02", "TC03"));
        assertNotNull(res);
        assertEquals(BigDecimal.valueOf(30000), res.basePrice());
        assertEquals(BigDecimal.valueOf(13000), res.optionsExtraTotal());
        assertEquals(BigDecimal.valueOf(43000), res.calculatedUnitPrice());
        assertEquals(3, res.validatedOptions().size());
    }

    @Test
    void validateFoodOptionsThrowsValidationExceptionWhenOptionNotFound() {
        Category cat = new Category("DM01", "Trà", "Trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> menuService.validateFoodOptions("MA01", List.of("TC_NON_EXISTENT")));
        assertTrue(ex.getMessage().contains("không tồn tại"));
    }

    @Test
    void validateFoodOptionsThrowsValidationExceptionWhenOptionBelongsToAnotherFood() {
        Category cat = new Category("DM01", "Nước", "Nước");
        categoryRepo.save(cat);
        Food food1 = new Food("MA01", cat, "Trà đào", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        Food food2 = new Food("MA02", cat, "Cà phê", BigDecimal.valueOf(25000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food1);
        foodRepo.save(food2);

        FoodOption optForCoffee = new FoodOption("TC02", food2, OptionType.TOPPING, "Thêm sữa", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        optionRepo.save(optForCoffee);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> menuService.validateFoodOptions("MA01", List.of("TC02")));
        assertTrue(ex.getMessage().contains("không thuộc về món ăn này"));
    }

    @Test
    void validateFoodOptionsThrowsValidationExceptionWhenOptionIsInactive() {
        Category cat = new Category("DM01", "Trà", "Trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà vải", BigDecimal.valueOf(32000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodOption opt = new FoodOption("TC01", food, OptionType.TOPPING, "Thạch vải", BigDecimal.valueOf(6000), OptionStatus.INACTIVE);
        optionRepo.save(opt);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> menuService.validateFoodOptions("MA01", List.of("TC01")));
        assertTrue(ex.getMessage().contains("ngừng áp dụng"));
    }

    @Test
    void validateFoodOptionsThrowsValidationExceptionWhenMultipleSizesSelected() {
        Category cat = new Category("DM01", "Trà", "Trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà sữa", BigDecimal.valueOf(30000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodOption s1 = new FoodOption("TC01", food, OptionType.SIZE, "Size M", BigDecimal.ZERO, OptionStatus.ACTIVE);
        FoodOption s2 = new FoodOption("TC02", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        optionRepo.save(s1);
        optionRepo.save(s2);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> menuService.validateFoodOptions("MA01", List.of("TC01", "TC02")));
        assertTrue(ex.getMessage().contains("tối đa 1 kích cỡ"));
    }

    @Test
    void validateFoodOptionsThrowsValidationExceptionWhenMultipleSugarLevelsSelected() {
        Category cat = new Category("DM01", "Trà", "Trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà chanh", BigDecimal.valueOf(25000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodOption d1 = new FoodOption("TC01", food, OptionType.SUGAR_LEVEL, "50% đường", BigDecimal.ZERO, OptionStatus.ACTIVE);
        FoodOption d2 = new FoodOption("TC02", food, OptionType.SUGAR_LEVEL, "100% đường", BigDecimal.ZERO, OptionStatus.ACTIVE);
        optionRepo.save(d1);
        optionRepo.save(d2);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> menuService.validateFoodOptions("MA01", List.of("TC01", "TC02")));
        assertTrue(ex.getMessage().contains("mức đường"));
    }

    @Test
    void validateFoodOptionsThrowsValidationExceptionWhenMultipleIceLevelsSelected() {
        Category cat = new Category("DM01", "Trà", "Trà");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà ô long", BigDecimal.valueOf(28000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        FoodOption ice1 = new FoodOption("TC01", food, OptionType.ICE_LEVEL, "50% đá", BigDecimal.ZERO, OptionStatus.ACTIVE);
        FoodOption ice2 = new FoodOption("TC02", food, OptionType.ICE_LEVEL, "Không đá", BigDecimal.ZERO, OptionStatus.ACTIVE);
        optionRepo.save(ice1);
        optionRepo.save(ice2);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> menuService.validateFoodOptions("MA01", List.of("TC01", "TC02")));
        assertTrue(ex.getMessage().contains("mức đá"));
    }

    @Test
    void updateFoodOptionStatusChangesStatusSuccessfully() {
        Category cat = new Category("DM01", "Nước", "Nước");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Trà tắc", BigDecimal.valueOf(20000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);
        FoodOption opt = new FoodOption("TC01", food, OptionType.TOPPING, "Trân châu", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        optionRepo.save(opt);

        FoodOptionResponse res = menuService.updateFoodOptionStatus("TC01", OptionStatus.INACTIVE);
        assertNotNull(res);
        assertEquals(OptionStatus.INACTIVE, res.status());

        FoodOption stored = optionRepo.findById("TC01").orElseThrow();
        assertEquals(OptionStatus.INACTIVE, stored.getStatus());
    }

    @Test
    void updateFoodOptionStatusThrowsWhenStatusIsNull() {
        assertThrows(ValidationException.class, () -> menuService.updateFoodOptionStatus("TC01", null));
    }

    @Test
    void checkMenuCartConsistencyReturnsConsistentWhenAllMatch() {
        Category cat = new Category("DM01", "Món chính", "Món chính");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Phở bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);
        FoodOption opt = new FoodOption("TC01", food, OptionType.TOPPING, "Trứng chần", BigDecimal.valueOf(5000), OptionStatus.ACTIVE);
        optionRepo.save(opt);

        CartConsistencyItemRequest req = new CartConsistencyItemRequest("MA01", BigDecimal.valueOf(55000), List.of("TC01"));
        CartConsistencyReport report = menuService.checkMenuCartConsistency(List.of(req));

        assertTrue(report.allConsistent());
        assertEquals(1, report.totalItemsChecked());
        assertEquals(0, report.invalidItemsCount());
        assertTrue(report.itemResults().get(0).consistent());
        assertTrue(report.itemResults().get(0).available());
        assertTrue(report.itemResults().get(0).optionsValid());
        assertTrue(report.itemResults().get(0).priceConsistent());
    }

    @Test
    void checkMenuCartConsistencyDetectsUnavailableFood() {
        Category cat = new Category("DM01", "Món chính", "Món chính");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Bún chả", BigDecimal.valueOf(45000), null, null, FoodStatus.UNAVAILABLE);
        foodRepo.save(food);

        CartConsistencyItemRequest req = new CartConsistencyItemRequest("MA01", BigDecimal.valueOf(45000), List.of());
        CartConsistencyReport report = menuService.checkMenuCartConsistency(List.of(req));

        assertFalse(report.allConsistent());
        assertEquals(1, report.invalidItemsCount());
        CartConsistencyItemResult itemRes = report.itemResults().get(0);
        assertFalse(itemRes.consistent());
        assertFalse(itemRes.available());
        assertEquals(FoodStatus.UNAVAILABLE, itemRes.foodStatus());
        assertTrue(itemRes.issueMessage().contains("tạm hết"));
    }

    @Test
    void checkMenuCartConsistencyDetectsDeletedOrUnknownFood() {
        CartConsistencyItemRequest req = new CartConsistencyItemRequest("MA_DELETED", BigDecimal.valueOf(40000), List.of());
        CartConsistencyReport report = menuService.checkMenuCartConsistency(List.of(req));

        assertFalse(report.allConsistent());
        assertEquals(1, report.invalidItemsCount());
        CartConsistencyItemResult itemRes = report.itemResults().get(0);
        assertFalse(itemRes.consistent());
        assertFalse(itemRes.available());
        assertTrue(itemRes.issueMessage().contains("không tồn tại"));
    }

    @Test
    void checkMenuCartConsistencyDetectsInactiveOption() {
        Category cat = new Category("DM01", "Món chính", "Món chính");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Cơm tấm", BigDecimal.valueOf(40000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);
        FoodOption opt = new FoodOption("TC01", food, OptionType.TOPPING, "Chả cua", BigDecimal.valueOf(10000), OptionStatus.INACTIVE);
        optionRepo.save(opt);

        CartConsistencyItemRequest req = new CartConsistencyItemRequest("MA01", BigDecimal.valueOf(50000), List.of("TC01"));
        CartConsistencyReport report = menuService.checkMenuCartConsistency(List.of(req));

        assertFalse(report.allConsistent());
        CartConsistencyItemResult itemRes = report.itemResults().get(0);
        assertFalse(itemRes.consistent());
        assertFalse(itemRes.optionsValid());
        assertTrue(itemRes.invalidOptionNames().contains("Chả cua"));
    }

    @Test
    void checkMenuCartConsistencyDetectsPriceChange() {
        Category cat = new Category("DM01", "Món chính", "Món chính");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Mì Quảng", BigDecimal.valueOf(55000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        CartConsistencyItemRequest req = new CartConsistencyItemRequest("MA01", BigDecimal.valueOf(50000), List.of());
        CartConsistencyReport report = menuService.checkMenuCartConsistency(List.of(req));

        assertFalse(report.allConsistent());
        CartConsistencyItemResult itemRes = report.itemResults().get(0);
        assertFalse(itemRes.consistent());
        assertFalse(itemRes.priceConsistent());
        assertEquals(BigDecimal.valueOf(55000), itemRes.currentUnitPrice());
        assertEquals(BigDecimal.valueOf(50000), itemRes.expectedUnitPrice());
        assertTrue(itemRes.issueMessage().contains("Giá món ăn đã cập nhật"));
    }

    @Test
    void checkMenuCartConsistencyHandlesEmptyList() {
        CartConsistencyReport report = menuService.checkMenuCartConsistency(List.of());
        assertTrue(report.allConsistent());
        assertEquals(0, report.totalItemsChecked());
    }

    @Test
    void deleteFoodThrowsConflictExceptionWhenConstraintFails() {
        Category cat = new Category("DM01", "Đồ ăn", "Đồ ăn");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Pizza", BigDecimal.valueOf(100000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);

        foodRepo.setFailOnDelete(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> menuService.deleteFood("MA01"));
        assertTrue(ex.getMessage().contains("đơn hàng hoặc giỏ hàng"));
    }

    @Test
    void deleteFoodOptionThrowsConflictExceptionWhenConstraintFails() {
        Category cat = new Category("DM01", "Đồ ăn", "Đồ ăn");
        categoryRepo.save(cat);
        Food food = new Food("MA01", cat, "Pizza", BigDecimal.valueOf(100000), null, null, FoodStatus.AVAILABLE);
        foodRepo.save(food);
        FoodOption opt = new FoodOption("TC01", food, OptionType.SIZE, "Size L", BigDecimal.valueOf(20000), OptionStatus.ACTIVE);
        optionRepo.save(opt);

        optionRepo.setFailOnDelete(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> menuService.deleteFoodOption("TC01"));
        assertTrue(ex.getMessage().contains("đơn hàng hoặc giỏ hàng"));
    }

    // =========================================================
    // Fake In-Memory Repositories for testing without database
    // =========================================================

    private static class FakeCategoryRepository extends CategoryDAO {
        private FakeCategoryRepository() { super(null); }
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

    private static class FakeFoodRepository extends FoodDAO {
        private FakeFoodRepository() { super(null); }
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

        private boolean failOnDelete = false;

        public void setFailOnDelete(boolean failOnDelete) {
            this.failOnDelete = failOnDelete;
        }

        @Override
        public void deleteById(String id) {
            if (failOnDelete) {
                throw new RuntimeException("Simulated foreign key constraint violation");
            }
            data.remove(id);
        }

        @Override
        public String generateNextId() {
            return "MA" + (data.size() + 1);
        }
    }

    private static class FakeFoodOptionRepository extends FoodOptionDAO {
        private FakeFoodOptionRepository() { super(null); }
        private final Map<String, FoodOption> data = new HashMap<>();
        private boolean failOnDelete = false;

        public void setFailOnDelete(boolean failOnDelete) {
            this.failOnDelete = failOnDelete;
        }

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
            if (failOnDelete) {
                throw new RuntimeException("Simulated foreign key constraint violation");
            }
            data.remove(id);
        }

        @Override
        public String generateNextId() {
            return "TC" + (data.size() + 1);
        }
    }
}
