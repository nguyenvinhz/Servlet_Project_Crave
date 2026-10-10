package com.foodordering.exception;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

public class ConflictException extends AppException {

    public ConflictException(String message) {
        super("CONFLICT", message, HttpServletResponse.SC_CONFLICT, Map.of());
    }

    public ConflictException(String message, Map<String, String> details) {
        super("CONFLICT", message, HttpServletResponse.SC_CONFLICT, details);
    }
}
