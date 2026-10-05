package com.foodordering.dto;

import java.util.Map;

public record ApiError(String code, String message, Map<String, String> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }
}
