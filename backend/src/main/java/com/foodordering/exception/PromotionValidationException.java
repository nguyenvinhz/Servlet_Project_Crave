package com.foodordering.exception;

import com.foodordering.enums.ErrorCode;

/**
 * Exception ném ra khi kiểm tra mã khuyến mãi thất bại.
 */
public class PromotionValidationException extends ApiException {
    public PromotionValidationException(ErrorCode errorCode) {
        super(errorCode, errorCode.getDefaultMessage(), 400);
    }

    public PromotionValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message, 400);
    }
}
