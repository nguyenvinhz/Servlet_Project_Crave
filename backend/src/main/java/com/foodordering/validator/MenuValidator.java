package com.foodordering.validator;

import com.foodordering.dto.CreateCategoryRequest;
import com.foodordering.dto.CreateFoodOptionRequest;
import com.foodordering.dto.CreateFoodRequest;
import com.foodordering.dto.UpdateCategoryRequest;
import com.foodordering.dto.UpdateFoodOptionRequest;
import com.foodordering.dto.UpdateFoodRequest;
import com.foodordering.exception.ValidationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class MenuValidator {

    private MenuValidator() {
    }

    public static void validateCreateCategory(CreateCategoryRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu yêu cầu không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();
        if (isBlank(request.name())) {
            errors.put("name", "Tên danh mục không được để trống.");
        } else if (request.name().trim().length() > 100) {
            errors.put("name", "Tên danh mục không vượt quá 100 ký tự.");
        }

        if (request.description() != null && request.description().length() > 255) {
            errors.put("description", "Mô tả danh mục không vượt quá 255 ký tự.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu danh mục không hợp lệ.", errors);
        }
    }

    public static void validateUpdateCategory(UpdateCategoryRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu yêu cầu không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();
        if (isBlank(request.name())) {
            errors.put("name", "Tên danh mục không được để trống.");
        } else if (request.name().trim().length() > 100) {
            errors.put("name", "Tên danh mục không vượt quá 100 ký tự.");
        }

        if (request.description() != null && request.description().length() > 255) {
            errors.put("description", "Mô tả danh mục không vượt quá 255 ký tự.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu cập nhật danh mục không hợp lệ.", errors);
        }
    }

    public static void validateCreateFood(CreateFoodRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu yêu cầu không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();
        if (isBlank(request.categoryId())) {
            errors.put("categoryId", "Mã danh mục không được để trống.");
        }
        if (isBlank(request.name())) {
            errors.put("name", "Tên món không được để trống.");
        } else if (request.name().trim().length() > 150) {
            errors.put("name", "Tên món không vượt quá 150 ký tự.");
        }
        if (request.price() == null) {
            errors.put("price", "Giá món không được để trống.");
        } else if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("price", "Giá món phải lớn hơn 0.");
        }
        if (request.imageUrl() != null && request.imageUrl().length() > 512) {
            errors.put("imageUrl", "Đường dẫn ảnh không vượt quá 512 ký tự.");
        }
        if (request.description() != null && request.description().length() > 500) {
            errors.put("description", "Mô tả món không vượt quá 500 ký tự.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu món ăn không hợp lệ.", errors);
        }
    }

    public static void validateUpdateFood(UpdateFoodRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu yêu cầu không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();
        if (isBlank(request.categoryId())) {
            errors.put("categoryId", "Mã danh mục không được để trống.");
        }
        if (isBlank(request.name())) {
            errors.put("name", "Tên món không được để trống.");
        } else if (request.name().trim().length() > 150) {
            errors.put("name", "Tên món không vượt quá 150 ký tự.");
        }
        if (request.price() == null) {
            errors.put("price", "Giá món không được để trống.");
        } else if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("price", "Giá món phải lớn hơn 0.");
        }
        if (request.imageUrl() != null && request.imageUrl().length() > 512) {
            errors.put("imageUrl", "Đường dẫn ảnh không vượt quá 512 ký tự.");
        }
        if (request.description() != null && request.description().length() > 500) {
            errors.put("description", "Mô tả món không vượt quá 500 ký tự.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu cập nhật món không hợp lệ.", errors);
        }
    }

    public static void validateCreateOption(CreateFoodOptionRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu yêu cầu không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();
        if (request.optionType() == null) {
            errors.put("optionType", "Loại tùy chọn không được để trống.");
        }
        if (isBlank(request.name())) {
            errors.put("name", "Tên tùy chọn không được để trống.");
        } else if (request.name().trim().length() > 100) {
            errors.put("name", "Tên tùy chọn không vượt quá 100 ký tự.");
        }
        if (request.extraPrice() != null && request.extraPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.put("extraPrice", "Giá phụ thu không được âm.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu tùy chọn không hợp lệ.", errors);
        }
    }

    public static void validateUpdateOption(UpdateFoodOptionRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu yêu cầu không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();
        if (request.optionType() == null) {
            errors.put("optionType", "Loại tùy chọn không được để trống.");
        }
        if (isBlank(request.name())) {
            errors.put("name", "Tên tùy chọn không được để trống.");
        } else if (request.name().trim().length() > 100) {
            errors.put("name", "Tên tùy chọn không vượt quá 100 ký tự.");
        }
        if (request.extraPrice() != null && request.extraPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.put("extraPrice", "Giá phụ thu không được âm.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu cập nhật tùy chọn không hợp lệ.", errors);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
