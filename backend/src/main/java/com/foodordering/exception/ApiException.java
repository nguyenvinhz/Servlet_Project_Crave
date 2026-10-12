package com.foodordering.exception;

import com.foodordering.enums.ErrorCode;

/**
 * Exception cơ sở cho các lỗi nghiệp vụ trong API.
 */
public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final int httpStatus;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = 400;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = 400;
    }

    public ApiException(ErrorCode errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
