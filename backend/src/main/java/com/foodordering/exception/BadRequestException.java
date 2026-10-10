package com.foodordering.exception;

import com.foodordering.enums.ErrorCode;

/**
 * Exception ném ra khi client gửi request không hợp lệ (HTTP 400).
 */
public class BadRequestException extends ApiException {
    public BadRequestException(ErrorCode errorCode) {
        super(errorCode, errorCode.getDefaultMessage(), 400);
    }

    public BadRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message, 400);
    }

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message, 400);
    }
}
