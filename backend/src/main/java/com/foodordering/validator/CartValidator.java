package com.foodordering.validator;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import java.util.HashSet;

/**
 * Kiểm tra tính hợp lệ của dữ liệu đầu vào cho các thao tác giỏ hàng.
 */
public class CartValidator {

    private CartValidator() {
    }

    public static void validateCustomerId(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.UNAUTHORIZED, "Mã khách hàng không được để trống hoặc chưa đăng nhập");
        }
    }

    public static void validateAddToCart(AddToCartRequest request) {
        if (request == null) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Dữ liệu yêu cầu thêm món không được để trống");
        }
        if (request.getFoodId() == null || request.getFoodId().trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Mã món ăn (foodId) không được để trống");
        }
        if (request.getQuantity() <= 0) {
            throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món thêm vào giỏ phải lớn hơn 0");
        }
        if (request.getQuantity() > 100) {
            throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng mỗi lần thêm không vượt quá 100 phần");
        }
        if (request.getNote() != null && request.getNote().length() > 255) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Ghi chú món không được vượt quá 255 ký tự");
        }
        if (request.getOptionIds() != null) {
            if (request.getOptionIds().stream().anyMatch(id -> id == null || id.isBlank())
                    || new HashSet<>(request.getOptionIds()).size() != request.getOptionIds().size()) {
                throw new BadRequestException(ErrorCode.INVALID_OPTION, "Tùy chọn món không được để trống hoặc trùng lặp");
            }
        }
    }

    public static void validateUpdateCartItem(UpdateCartItemRequest request) {
        if (request == null) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Dữ liệu yêu cầu cập nhật không được để trống");
        }
        if (request.getCartItemId() == null || request.getCartItemId().trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Mã mục giỏ hàng (cartItemId) không được để trống");
        }
        if (request.getQuantity() < 0) {
            throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món không được là số âm");
        }
        if (request.getQuantity() > 100) {
            throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món không được vượt quá 100 phần");
        }
        if (request.getNote() != null && request.getNote().length() > 255) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Ghi chú món không được vượt quá 255 ký tự");
        }
        validateOptionIds(request.getOptionIds());
    }

    private static void validateOptionIds(java.util.List<String> optionIds) {
        if (optionIds != null && (optionIds.stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(optionIds).size() != optionIds.size())) {
            throw new BadRequestException(ErrorCode.INVALID_OPTION, "Tùy chọn món không được để trống hoặc trùng lặp");
        }
    }

    public static void validateCartItemId(String cartItemId) {
        if (cartItemId == null || cartItemId.trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Mã mục giỏ hàng không được để trống");
        }
    }
}
