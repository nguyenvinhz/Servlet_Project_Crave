package com.foodordering.exception;

import com.foodordering.enums.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, HttpServletResponse.SC_NOT_FOUND);
    }

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode != null ? errorCode.getCode() : "NOT_FOUND",
                errorCode != null ? errorCode.getDefaultMessage() : "Resource not found",
                HttpServletResponse.SC_NOT_FOUND);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode != null ? errorCode.getCode() : "NOT_FOUND",
                message,
                HttpServletResponse.SC_NOT_FOUND);
    }
}
