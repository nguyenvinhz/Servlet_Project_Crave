package com.foodordering.exception;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

public class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpServletResponse.SC_FORBIDDEN, Map.of());
    }

    public ForbiddenException(String message, Map<String, String> details) {
        super("FORBIDDEN", message, HttpServletResponse.SC_FORBIDDEN, details);
    }
}
