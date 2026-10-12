package com.foodordering.validator;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CartValidatorTest {

    @Test
    @DisplayName("validateCustomerId chặn customerId null hoặc rỗng")
    void testValidateCustomerId() {
        BadRequestException exNull = assertThrows(BadRequestException.class, () -> CartValidator.validateCustomerId(null));
        assertEquals(ErrorCode.UNAUTHORIZED, exNull.getErrorCode());

        BadRequestException exBlank = assertThrows(BadRequestException.class, () -> CartValidator.validateCustomerId("   "));
        assertEquals(ErrorCode.UNAUTHORIZED, exBlank.getErrorCode());

        assertDoesNotThrow(() -> CartValidator.validateCustomerId("KH01"));
    }

    @Test
    @DisplayName("validateAddToCart kiểm tra dữ liệu đầu vào: số lượng âm, vượt quá 100, foodId rỗng")
    void testValidateAddToCart() {
        // Null request
        BadRequestException exNull = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(null));
        assertEquals(ErrorCode.BAD_REQUEST, exNull.getErrorCode());

        // FoodId rỗng
        AddToCartRequest reqNoFood = new AddToCartRequest("", 1, null, Collections.emptyList());
        BadRequestException exNoFood = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqNoFood));
        assertEquals(ErrorCode.BAD_REQUEST, exNoFood.getErrorCode());

        // Số lượng = 0
        AddToCartRequest reqZero = new AddToCartRequest("FOOD01", 0, null, Collections.emptyList());
        BadRequestException exZero = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqZero));
        assertEquals(ErrorCode.INVALID_QUANTITY, exZero.getErrorCode());

        // Số lượng âm
        AddToCartRequest reqNeg = new AddToCartRequest("FOOD01", -5, null, Collections.emptyList());
        BadRequestException exNeg = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqNeg));
        assertEquals(ErrorCode.INVALID_QUANTITY, exNeg.getErrorCode());

        // Số lượng vượt quá 100
        AddToCartRequest reqOver = new AddToCartRequest("FOOD01", 101, null, Collections.emptyList());
        BadRequestException exOver = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqOver));
        assertEquals(ErrorCode.INVALID_QUANTITY, exOver.getErrorCode());

        // Hợp lệ
        AddToCartRequest reqValid = new AddToCartRequest("FOOD01", 5, "Không cay", Collections.emptyList());
        assertDoesNotThrow(() -> CartValidator.validateAddToCart(reqValid));
    }

    @Test
    @DisplayName("validateUpdateCartItem kiểm tra số lượng âm, vượt quá 100, cartItemId rỗng")
    void testValidateUpdateCartItem() {
        // Null request
        BadRequestException exNull = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(null));
        assertEquals(ErrorCode.BAD_REQUEST, exNull.getErrorCode());

        // CartItemId rỗng
        UpdateCartItemRequest reqNoId = new UpdateCartItemRequest("", 1, null);
        BadRequestException exNoId = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(reqNoId));
        assertEquals(ErrorCode.BAD_REQUEST, exNoId.getErrorCode());

        // Số lượng âm
        UpdateCartItemRequest reqNeg = new UpdateCartItemRequest("CTGH01", -1, null);
        BadRequestException exNeg = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(reqNeg));
        assertEquals(ErrorCode.INVALID_QUANTITY, exNeg.getErrorCode());

        // Số lượng = 0 (hợp lệ để xóa món)
        UpdateCartItemRequest reqZero = new UpdateCartItemRequest("CTGH01", 0, null);
        assertDoesNotThrow(() -> CartValidator.validateUpdateCartItem(reqZero));

        // Số lượng vượt quá 100
        UpdateCartItemRequest reqOver = new UpdateCartItemRequest("CTGH01", 101, null);
        BadRequestException exOver = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(reqOver));
        assertEquals(ErrorCode.INVALID_QUANTITY, exOver.getErrorCode());

        // Hợp lệ
        UpdateCartItemRequest reqValid = new UpdateCartItemRequest("CTGH01", 10, "Thêm đá");
        assertDoesNotThrow(() -> CartValidator.validateUpdateCartItem(reqValid));
    }

    @Test
    @DisplayName("validateCartItemId chặn cartItemId null hoặc rỗng")
    void testValidateCartItemId() {
        BadRequestException exNull = assertThrows(BadRequestException.class, () -> CartValidator.validateCartItemId(null));
        assertEquals(ErrorCode.BAD_REQUEST, exNull.getErrorCode());

        BadRequestException exBlank = assertThrows(BadRequestException.class, () -> CartValidator.validateCartItemId("   "));
        assertEquals(ErrorCode.BAD_REQUEST, exBlank.getErrorCode());

        assertDoesNotThrow(() -> CartValidator.validateCartItemId("CTGH01"));
    }
}
