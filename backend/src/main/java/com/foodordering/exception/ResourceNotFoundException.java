package com.foodordering.exception;

import com.foodordering.enums.ErrorCode;

/**
 * Exception ném ra khi không tìm thấy tài nguyên (HTTP 404).
 */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode, errorCode.getDefaultMessage(), 404);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message, 404);
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message, 404);
    }
}
