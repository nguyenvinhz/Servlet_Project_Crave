package com.foodordering.validator;

import com.foodordering.dto.CategoryRequest;
import com.foodordering.dto.FoodOptionRequest;
import com.foodordering.dto.FoodRequest;
import com.foodordering.exception.ValidationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class MenuValidator {

    private MenuValidator() {
    }

    public static void validateCategory(CategoryRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu danh mục không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();

        if (request.name() == null || request.name().trim().isEmpty()) {
            errors.put("name", "Tên danh mục không được để trống.");
        } else if (request.name().trim().length() > 100) {
            errors.put("name", "Tên danh mục không được vượt quá 100 ký tự.");
        } else if (request.name().contains("<") || request.name().contains(">")) {
            errors.put("name", "Tên danh mục không được chứa ký tự HTML.");
        }

        if (request.description() != null && request.description().trim().length() > 255) {
            errors.put("description", "Mô tả không được vượt quá 255 ký tự.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu danh mục không hợp lệ.", errors);
        }
    }

    public static void validateFood(FoodRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu món ăn không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();

        if (request.categoryId() == null || request.categoryId().trim().isEmpty()) {
            errors.put("categoryId", "Danh mục món ăn không được để trống.");
        }

        if (request.name() == null || request.name().trim().isEmpty()) {
            errors.put("name", "Tên món ăn không được để trống.");
        } else if (request.name().trim().length() > 150) {
            errors.put("name", "Tên món ăn không được vượt quá 150 ký tự.");
        } else if (request.name().contains("<") || request.name().contains(">")) {
            errors.put("name", "Tên món ăn không được chứa ký tự HTML.");
        }

        if (request.price() == null) {
            errors.put("price", "Giá món ăn không được để trống.");
        } else if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("price", "Giá món ăn phải lớn hơn 0.");
        }

        if (request.description() != null && request.description().trim().length() > 500) {
            errors.put("description", "Mô tả không được vượt quá 500 ký tự.");
        }

        if (request.imageUrl() != null && request.imageUrl().trim().length() > 512) {
            errors.put("imageUrl", "Đường dẫn ảnh không được vượt quá 512 ký tự.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu món ăn không hợp lệ.", errors);
        }
    }

    public static void validateFoodOption(FoodOptionRequest request) {
        if (request == null) {
            throw new ValidationException("Dữ liệu tùy chọn không được để trống.");
        }
        Map<String, String> errors = new HashMap<>();

        if (request.optionType() == null) {
            errors.put("optionType", "Loại tùy chọn không được để trống.");
        }

        if (request.name() == null || request.name().trim().isEmpty()) {
            errors.put("name", "Tên tùy chọn không được để trống.");
        } else if (request.name().trim().length() > 100) {
            errors.put("name", "Tên tùy chọn không được vượt quá 100 ký tự.");
        } else if (request.name().contains("<") || request.name().contains(">")) {
            errors.put("name", "Tên tùy chọn không được chứa ký tự HTML.");
        }

        if (request.extraPrice() == null) {
            errors.put("extraPrice", "Giá phụ thu không được để trống.");
        } else if (request.extraPrice().compareTo(BigDecimal.ZERO) < 0) {
            errors.put("extraPrice", "Giá phụ thu không được là số âm.");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Dữ liệu tùy chọn không hợp lệ.", errors);
        }
    }
}
