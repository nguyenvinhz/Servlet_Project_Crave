package com.foodordering.exception;

import jakarta.servlet.http.HttpServletResponse;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message, HttpServletResponse.SC_NOT_FOUND);
    }
}
