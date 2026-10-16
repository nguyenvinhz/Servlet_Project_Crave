package com.foodordering.validator;

import com.foodordering.bean.AddToCartForm;
import com.foodordering.bean.UpdateCartItemForm;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CartValidatorTest {

    @Test
    void updateRejectsMalformedOptionsButAllowsPreservingOrClearingThem() {
        UpdateCartItemForm request = new UpdateCartItemForm("CTGH01", 1, null);
        assertDoesNotThrow(() -> CartValidator.validateUpdateCartItem(request));
        request.setOptionIds(List.of());
        assertDoesNotThrow(() -> CartValidator.validateUpdateCartItem(request));
        for (List<String> options : List.of(List.of("TC01", "TC01"), List.of(" "), Arrays.asList((String) null))) {
            request.setOptionIds(options);
            BadRequestException failure = assertThrows(BadRequestException.class,
                    () -> CartValidator.validateUpdateCartItem(request));
            assertEquals(ErrorCode.INVALID_OPTION, failure.getErrorCode());
        }
    }

    @Test
    void addToCartRejectsDuplicateAndBlankOptionIds() {
        for (List<String> options : List.of(List.of("TC01", "TC01"), List.of(""), Arrays.asList((String) null))) {
            AddToCartForm request = new AddToCartForm("MA01", 1, null, options);
            BadRequestException failure = assertThrows(BadRequestException.class,
                    () -> CartValidator.validateAddToCart(request));
            assertEquals(ErrorCode.INVALID_OPTION, failure.getErrorCode());
        }
    }

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
        AddToCartForm reqNoFood = new AddToCartForm("", 1, null, Collections.emptyList());
        BadRequestException exNoFood = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqNoFood));
        assertEquals(ErrorCode.BAD_REQUEST, exNoFood.getErrorCode());

        // Số lượng = 0
        AddToCartForm reqZero = new AddToCartForm("FOOD01", 0, null, Collections.emptyList());
        BadRequestException exZero = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqZero));
        assertEquals(ErrorCode.INVALID_QUANTITY, exZero.getErrorCode());

        // Số lượng âm
        AddToCartForm reqNeg = new AddToCartForm("FOOD01", -5, null, Collections.emptyList());
        BadRequestException exNeg = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqNeg));
        assertEquals(ErrorCode.INVALID_QUANTITY, exNeg.getErrorCode());

        // Số lượng vượt quá 100
        AddToCartForm reqOver = new AddToCartForm("FOOD01", 101, null, Collections.emptyList());
        BadRequestException exOver = assertThrows(BadRequestException.class, () -> CartValidator.validateAddToCart(reqOver));
        assertEquals(ErrorCode.INVALID_QUANTITY, exOver.getErrorCode());

        // Hợp lệ
        AddToCartForm reqValid = new AddToCartForm("FOOD01", 5, "Không cay", Collections.emptyList());
        assertDoesNotThrow(() -> CartValidator.validateAddToCart(reqValid));
    }

    @Test
    @DisplayName("validateUpdateCartItem kiểm tra số lượng âm, vượt quá 100, cartItemId rỗng")
    void testValidateUpdateCartItem() {
        // Null request
        BadRequestException exNull = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(null));
        assertEquals(ErrorCode.BAD_REQUEST, exNull.getErrorCode());

        // CartItemId rỗng
        UpdateCartItemForm reqNoId = new UpdateCartItemForm("", 1, null);
        BadRequestException exNoId = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(reqNoId));
        assertEquals(ErrorCode.BAD_REQUEST, exNoId.getErrorCode());

        // Số lượng âm
        UpdateCartItemForm reqNeg = new UpdateCartItemForm("CTGH01", -1, null);
        BadRequestException exNeg = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(reqNeg));
        assertEquals(ErrorCode.INVALID_QUANTITY, exNeg.getErrorCode());

        // Số lượng = 0 (hợp lệ để xóa món)
        UpdateCartItemForm reqZero = new UpdateCartItemForm("CTGH01", 0, null);
        assertDoesNotThrow(() -> CartValidator.validateUpdateCartItem(reqZero));

        // Số lượng vượt quá 100
        UpdateCartItemForm reqOver = new UpdateCartItemForm("CTGH01", 101, null);
        BadRequestException exOver = assertThrows(BadRequestException.class, () -> CartValidator.validateUpdateCartItem(reqOver));
        assertEquals(ErrorCode.INVALID_QUANTITY, exOver.getErrorCode());

        // Hợp lệ
        UpdateCartItemForm reqValid = new UpdateCartItemForm("CTGH01", 10, "Thêm đá");
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
