package com.foodordering.exception;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

public class ValidationException extends AppException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, HttpServletResponse.SC_BAD_REQUEST, Map.of());
    }

    public ValidationException(String message, Map<String, String> details) {
        super("VALIDATION_ERROR", message, HttpServletResponse.SC_BAD_REQUEST, details);
    }
}
