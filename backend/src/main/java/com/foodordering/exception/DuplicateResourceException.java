package com.foodordering.exception;

import jakarta.servlet.http.HttpServletResponse;

public class DuplicateResourceException extends AppException {

    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message, HttpServletResponse.SC_CONFLICT);
    }
}
