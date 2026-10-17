package vn.edu.crave.model.service;

import vn.edu.crave.model.form.CartConsistencyItemRequest;
import vn.edu.crave.model.viewmodel.CartConsistencyItemResult;
import vn.edu.crave.model.viewmodel.CartConsistencyReport;
import vn.edu.crave.model.form.CategoryRequest;
import vn.edu.crave.model.viewmodel.CategoryResponse;
import vn.edu.crave.model.viewmodel.FoodDetailResponse;
import vn.edu.crave.model.form.FoodOptionRequest;
import vn.edu.crave.model.viewmodel.FoodOptionResponse;
import vn.edu.crave.model.viewmodel.FoodOptionValidationResult;
import vn.edu.crave.model.form.FoodRequest;
import vn.edu.crave.model.viewmodel.FoodSummaryResponse;
import vn.edu.crave.model.entity.Category;
import vn.edu.crave.model.entity.Food;
import vn.edu.crave.model.entity.FoodOption;
import vn.edu.crave.model.enums.FoodStatus;
import vn.edu.crave.model.enums.OptionStatus;
import vn.edu.crave.model.enums.OptionType;
import vn.edu.crave.exception.ConflictException;
import vn.edu.crave.exception.ResourceNotFoundException;
import vn.edu.crave.exception.ValidationException;
import vn.edu.crave.model.dao.CategoryDAO;
import vn.edu.crave.model.dao.FoodOptionDAO;
import vn.edu.crave.model.dao.FoodDAO;
import vn.edu.crave.model.validator.MenuValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service nghiệp vụ Menu, Quản lý món ăn, Danh mục và Tùy chọn món (Ngày 2).
 */
public class MenuService {

    private final CategoryDAO categoryDAO;
    private final FoodDAO foodDAO;
    private final FoodOptionDAO foodOptionDAO;

    public MenuService() {
        this(new CategoryDAO(), new FoodDAO(), new FoodOptionDAO());
    }

    public MenuService(CategoryDAO categoryDAO,
                       FoodDAO foodDAO,
                       FoodOptionDAO foodOptionDAO) {
        this.categoryDAO = categoryDAO;
        this.foodDAO = foodDAO;
        this.foodOptionDAO = foodOptionDAO;
    }

    // ==========================================
    // Category Operations
    // ==========================================

    public List<CategoryResponse> getCategories() {
        List<Category> categories = categoryDAO.findAll();
        List<CategoryResponse> responses = new ArrayList<>();
        for (Category category : categories) {
            long count = foodDAO.countByCategoryId(category.getId());
            List<String> imageUrls = getFoodImageUrlsForCategory(category.getId());
            responses.add(createCategoryResponse(category, count, imageUrls));
        }
        return responses;
    }

    public CategoryResponse getCategoryById(String id) {
        Category category = categoryDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id));
        long count = foodDAO.countByCategoryId(category.getId());
        List<String> imageUrls = getFoodImageUrlsForCategory(category.getId());
        return createCategoryResponse(category, count, imageUrls);
    }

    private List<String> getFoodImageUrlsForCategory(String categoryId) {
        List<Food> foods = foodDAO.findByCategoryId(categoryId);
        if (foods == null || foods.isEmpty()) {
            return List.of();
        }
        return foods.stream()
                .filter(f -> f.getImageUrl() != null && !f.getImageUrl().isBlank())
                .sorted((f1, f2) -> {
                    if (f1.getStatus() == FoodStatus.AVAILABLE && f2.getStatus() != FoodStatus.AVAILABLE) return -1;
                    if (f1.getStatus() != FoodStatus.AVAILABLE && f2.getStatus() == FoodStatus.AVAILABLE) return 1;
                    return 0;
                })
                .map(Food::getImageUrl)
                .distinct()
                .toList();
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        MenuValidator.validateCategory(request);
        String trimmedName = request.name().trim();
        if (categoryDAO.existsByName(trimmedName)) {
            throw new ConflictException("Tên danh mục '" + trimmedName + "' đã tồn tại.");
        }

        String id = categoryDAO.generateNextId();
        String description = request.description() != null ? request.description().trim() : null;
        Category category = new Category(id, trimmedName, description);
        Category saved = categoryDAO.save(category);
        return createCategoryResponse(saved, 0L, List.of());
    }

    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        MenuValidator.validateCategory(request);
        Category category = categoryDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id));

        String trimmedName = request.name().trim();
        if (categoryDAO.existsByNameAndIdNot(trimmedName, id)) {
            throw new ConflictException("Tên danh mục '" + trimmedName + "' đã tồn tại.");
        }

        category.setName(trimmedName);
        category.setDescription(request.description() != null ? request.description().trim() : null);
        Category updated = categoryDAO.update(category);
        long count = foodDAO.countByCategoryId(id);
        List<String> imageUrls = getFoodImageUrlsForCategory(id);
        return createCategoryResponse(updated, count, imageUrls);
    }

    public void deleteCategory(String id) {
        if (!categoryDAO.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + id);
        }
        if (foodDAO.countByCategoryId(id) > 0) {
            throw new ConflictException("Không thể xóa danh mục đang có món ăn.");
        }
        categoryDAO.deleteById(id);
    }

    // ==========================================
    // Food Operations
    // ==========================================

    public List<FoodSummaryResponse> getMenu(String categoryId, String keyword) {
        List<Food> foods = foodDAO.search(keyword, categoryId, FoodStatus.AVAILABLE);
        return foods.stream()
                .map(this::createFoodSummaryResponse)
                .collect(Collectors.toList());
    }

    public List<FoodSummaryResponse> getAllFoodsForAdmin(String categoryId, String keyword, FoodStatus status) {
        List<Food> foods = foodDAO.search(keyword, categoryId, status);
        return foods.stream()
                .map(this::createFoodSummaryResponse)
                .collect(Collectors.toList());
    }

    public FoodDetailResponse getFoodDetail(String id, boolean onlyActiveOptions) {
        Food food = foodDAO.findByIdWithOptions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        return createFoodDetailResponse(food, onlyActiveOptions);
    }

    public FoodDetailResponse createFood(FoodRequest request) {
        MenuValidator.validateFood(request);
        Category category = categoryDAO.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + request.categoryId()));

        String id = foodDAO.generateNextId();
        FoodStatus status = request.status() != null ? request.status() : FoodStatus.AVAILABLE;
        String imageUrl = request.imageUrl() != null ? request.imageUrl().trim() : null;
        String description = request.description() != null ? request.description().trim() : null;

        Food food = new Food(id, category, request.name().trim(), request.price(), imageUrl, description, status);
        Food saved = foodDAO.save(food);
        return createFoodDetailResponse(saved, false);
    }

    public FoodDetailResponse updateFood(String id, FoodRequest request) {
        MenuValidator.validateFood(request);
        Food food = foodDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        Category category = categoryDAO.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với mã: " + request.categoryId()));

        food.setCategory(category);
        food.setName(request.name().trim());
        food.setPrice(request.price());
        food.setImageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null);
        food.setDescription(request.description() != null ? request.description().trim() : null);
        if (request.status() != null) {
            food.setStatus(request.status());
        }

        foodDAO.update(food);
        Food reloaded = foodDAO.findByIdWithOptions(id).orElse(food);
        return createFoodDetailResponse(reloaded, false);
    }

    public FoodDetailResponse updateFoodStatus(String id, FoodStatus newStatus) {
        if (newStatus == null) {
            throw new ValidationException("Trạng thái món ăn không được để trống.");
        }
        Food food = foodDAO.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id));

        food.setStatus(newStatus);
        foodDAO.update(food);
        Food reloaded = foodDAO.findByIdWithOptions(id).orElse(food);
        return createFoodDetailResponse(reloaded, false);
    }

    public void deleteFood(String id) {
        if (!foodDAO.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + id);
        }
        try {
            foodDAO.deleteById(id);
        } catch (Exception e) {
            throw new ConflictException("Không thể xóa món ăn đang tồn tại trong đơn hàng hoặc giỏ hàng. Hãy chuyển trạng thái món sang Tạm hết món (UNAVAILABLE).");
        }
    }

    /**
     * Xác thực món ăn tồn tại và đang trong trạng thái phục vụ (AVAILABLE).
     */
    public Food validateFoodAvailable(String foodId) {
        if (foodId == null || foodId.isBlank()) {
            throw new ValidationException("Mã món ăn không được để trống.");
        }
        Food food = foodDAO.findById(foodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + foodId));
        if (food.getStatus() != FoodStatus.AVAILABLE) {
            throw new ValidationException("Món ăn '" + food.getName() + "' hiện đang tạm ngưng phục vụ.");
        }
        return food;
    }

    /**
     * Xác thực các tùy chọn của món ăn, kiểm tra trạng thái ACTIVE, kiểm tra xung đột nhóm và tính giá chuẩn xác từ máy chủ.
     */
    public FoodOptionValidationResult validateFoodOptions(String foodId, List<String> optionIds) {
        Food food = validateFoodAvailable(foodId);
        BigDecimal basePrice = food.getPrice() != null ? food.getPrice() : BigDecimal.ZERO;
        BigDecimal optionsTotal = BigDecimal.ZERO;
        List<FoodOption> validatedOptions = new ArrayList<>();

        if (optionIds != null && !optionIds.isEmpty()) {
            int sizeCount = 0;
            int sugarCount = 0;
            int iceCount = 0;

            for (String optionId : optionIds) {
                if (optionId == null || optionId.isBlank() || "0".equals(optionId)) {
                    continue;
                }
                FoodOption option = foodOptionDAO.findById(optionId)
                        .orElseThrow(() -> new ValidationException("Tùy chọn với mã '" + optionId + "' không tồn tại trong hệ thống."));

                if (option.getFood() == null || !foodId.equals(option.getFood().getId())) {
                    throw new ValidationException("Tùy chọn '" + option.getName() + "' không thuộc về món ăn này.");
                }

                if (option.getStatus() != OptionStatus.ACTIVE) {
                    throw new ValidationException("Tùy chọn '" + option.getName() + "' hiện đã ngừng áp dụng.");
                }

                if (option.getOptionType() == OptionType.SIZE) {
                    sizeCount++;
                    if (sizeCount > 1) {
                        throw new ValidationException("Chỉ được chọn tối đa 1 kích cỡ (Size) cho món ăn.");
                    }
                } else if (option.getOptionType() == OptionType.SUGAR_LEVEL) {
                    sugarCount++;
                    if (sugarCount > 1) {
                        throw new ValidationException("Chỉ được chọn tối đa 1 mức đường cho món ăn.");
                    }
                } else if (option.getOptionType() == OptionType.ICE_LEVEL) {
                    iceCount++;
                    if (iceCount > 1) {
                        throw new ValidationException("Chỉ được chọn tối đa 1 mức đá cho món ăn.");
                    }
                }

                if (option.getExtraPrice() != null) {
                    optionsTotal = optionsTotal.add(option.getExtraPrice());
                }
                validatedOptions.add(option);
            }
        }

        BigDecimal calculatedUnitPrice = basePrice.add(optionsTotal);
        return new FoodOptionValidationResult(food, validatedOptions, basePrice, optionsTotal, calculatedUnitPrice);
    }

    /**
     * Kiểm tra tính nhất quán giữa dữ liệu món trong giỏ hàng và thực đơn thực tế (giá, trạng thái món, trạng thái option).
     */
    public CartConsistencyReport checkMenuCartConsistency(List<CartConsistencyItemRequest> items) {
        if (items == null || items.isEmpty()) {
            return new CartConsistencyReport(true, 0, 0, List.of());
        }

        List<CartConsistencyItemResult> results = new ArrayList<>();
        int invalidCount = 0;

        for (CartConsistencyItemRequest item : items) {
            String foodId = item.foodId();
            Optional<Food> foodOpt = foodDAO.findById(foodId);

            if (foodOpt.isEmpty()) {
                invalidCount++;
                results.add(new CartConsistencyItemResult(
                        foodId,
                        "Món không xác định",
                        false,
                        false,
                        FoodStatus.UNAVAILABLE,
                        false,
                        List.of(),
                        false,
                        BigDecimal.ZERO,
                        item.expectedUnitPrice(),
                        "Món ăn không tồn tại hoặc đã bị xóa khỏi thực đơn."
                ));
                continue;
            }

            Food food = foodOpt.get();
            boolean available = food.getStatus() == FoodStatus.AVAILABLE;
            BigDecimal basePrice = food.getPrice() != null ? food.getPrice() : BigDecimal.ZERO;

            boolean optionsValid = true;
            List<String> invalidOptionNames = new ArrayList<>();
            BigDecimal optionsExtraTotal = BigDecimal.ZERO;

            if (item.optionIds() != null && !item.optionIds().isEmpty()) {
                for (String optId : item.optionIds()) {
                    if (optId == null || optId.isBlank() || "0".equals(optId)) {
                        continue;
                    }
                    Optional<FoodOption> opt = foodOptionDAO.findById(optId);
                    if (opt.isEmpty() || opt.get().getFood() == null || !foodId.equals(opt.get().getFood().getId())
                            || opt.get().getStatus() != OptionStatus.ACTIVE) {
                        optionsValid = false;
                        String optName = opt.map(FoodOption::getName).orElse("Mã " + optId);
                        invalidOptionNames.add(optName);
                    } else {
                        if (opt.get().getExtraPrice() != null) {
                            optionsExtraTotal = optionsExtraTotal.add(opt.get().getExtraPrice());
                        }
                    }
                }
            }

            BigDecimal currentUnitPrice = basePrice.add(optionsExtraTotal);
            boolean priceConsistent = item.expectedUnitPrice() == null
                    || item.expectedUnitPrice().compareTo(currentUnitPrice) == 0;

            boolean consistent = available && optionsValid && priceConsistent;
            if (!consistent) {
                invalidCount++;
            }

            StringBuilder issueMsg = new StringBuilder();
            if (!available) {
                issueMsg.append("Món ăn hiện tạm hết/ngưng phục vụ. ");
            }
            if (!optionsValid) {
                issueMsg.append("Tùy chọn không hợp lệ hoặc đã ngừng áp dụng: ")
                        .append(String.join(", ", invalidOptionNames)).append(". ");
            }
            if (!priceConsistent) {
                issueMsg.append("Giá món ăn đã cập nhật (hiện tại: ")
                        .append(currentUnitPrice).append(" ₫). ");
            }

            results.add(new CartConsistencyItemResult(
                    foodId,
                    food.getName(),
                    consistent,
                    available,
                    food.getStatus(),
                    optionsValid,
                    invalidOptionNames,
                    priceConsistent,
                    currentUnitPrice,
                    item.expectedUnitPrice(),
                    issueMsg.toString().trim()
            ));
        }

        return new CartConsistencyReport(invalidCount == 0, items.size(), invalidCount, results);
    }

    // ==========================================
    // Food Option Operations
    // ==========================================

    public List<FoodOptionResponse> getOptionsByFoodId(String foodId, boolean onlyActive) {
        if (!foodDAO.existsById(foodId)) {
            throw new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + foodId);
        }
        List<FoodOption> options = onlyActive
                ? foodOptionDAO.findActiveByFoodId(foodId)
                : foodOptionDAO.findByFoodId(foodId);

        return options.stream()
                .map(this::createFoodOptionResponse)
                .collect(Collectors.toList());
    }

    public FoodOptionResponse getFoodOptionById(String optionId) {
        FoodOption option = foodOptionDAO.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId));
        return createFoodOptionResponse(option);
    }

    public FoodOptionResponse createFoodOption(String foodId, FoodOptionRequest request) {
        MenuValidator.validateFoodOption(request);
        Food food = foodDAO.findById(foodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với mã: " + foodId));

        String trimmedName = request.name().trim();
        if (foodOptionDAO.existsByFoodIdAndTypeAndName(foodId, request.optionType(), trimmedName)) {
            throw new ConflictException("Tùy chọn '" + trimmedName + "' thuộc nhóm " + request.optionType() + " đã tồn tại cho món này.");
        }

        String id = foodOptionDAO.generateNextId();
        BigDecimal extraPrice = request.extraPrice() != null ? request.extraPrice() : BigDecimal.ZERO;
        OptionStatus status = request.status() != null ? request.status() : OptionStatus.ACTIVE;

        FoodOption option = new FoodOption(id, food, request.optionType(), trimmedName, extraPrice, status);
        FoodOption saved = foodOptionDAO.save(option);
        return createFoodOptionResponse(saved);
    }

    public FoodOptionResponse updateFoodOption(String optionId, FoodOptionRequest request) {
        MenuValidator.validateFoodOption(request);
        FoodOption option = foodOptionDAO.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId));

        String foodId = option.getFood().getId();
        String trimmedName = request.name().trim();
        if (foodOptionDAO.existsByFoodIdAndTypeAndNameAndIdNot(foodId, request.optionType(), trimmedName, optionId)) {
            throw new ConflictException("Tùy chọn '" + trimmedName + "' thuộc nhóm " + request.optionType() + " đã tồn tại cho món này.");
        }

        option.setOptionType(request.optionType());
        option.setName(trimmedName);
        option.setExtraPrice(request.extraPrice() != null ? request.extraPrice() : BigDecimal.ZERO);
        if (request.status() != null) {
            option.setStatus(request.status());
        }

        FoodOption updated = foodOptionDAO.update(option);
        return createFoodOptionResponse(updated);
    }

    public FoodOptionResponse updateFoodOptionStatus(String optionId, OptionStatus newStatus) {
        if (newStatus == null) {
            throw new ValidationException("Trạng thái tùy chọn không được để trống.");
        }
        FoodOption option = foodOptionDAO.findById(optionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId));

        option.setStatus(newStatus);
        FoodOption updated = foodOptionDAO.update(option);
        return createFoodOptionResponse(updated);
    }

    public void deleteFoodOption(String optionId) {
        if (!foodOptionDAO.existsById(optionId)) {
            throw new ResourceNotFoundException("Không tìm thấy tùy chọn với mã: " + optionId);
        }
        try {
            foodOptionDAO.deleteById(optionId);
        } catch (Exception e) {
            throw new ConflictException("Không thể xóa tùy chọn đang tồn tại trong đơn hàng hoặc giỏ hàng. Hãy chuyển trạng thái tùy chọn sang Không áp dụng (INACTIVE).");
        }
    }

    private CategoryResponse createCategoryResponse(Category category, long foodCount, List<String> imageUrls) {
        if (category == null) {
            return null;
        }
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                foodCount,
                imageUrls != null ? imageUrls : List.of()
        );
    }

    private FoodOptionResponse createFoodOptionResponse(FoodOption option) {
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

    private FoodSummaryResponse createFoodSummaryResponse(Food food) {
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

    private FoodDetailResponse createFoodDetailResponse(Food food, boolean onlyActiveOptions) {
        if (food == null) {
            return null;
        }
        List<FoodOptionResponse> options;
        if (food.getOptions() == null) {
            options = List.of();
        } else {
            options = food.getOptions().stream()
                    .filter(opt -> !onlyActiveOptions || opt.getStatus() == OptionStatus.ACTIVE)
                    .map(this::createFoodOptionResponse)
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
