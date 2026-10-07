package com.foodordering.exception;

import java.util.Collections;
import java.util.Map;

public abstract class AppException extends RuntimeException {

    private final String errorCode;
    private final int statusCode;
    private final Map<String, String> details;

    protected AppException(String errorCode, String message, int statusCode) {
        this(errorCode, message, statusCode, Collections.emptyMap());
    }

    protected AppException(String errorCode, String message, int statusCode, Map<String, String> details) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.details = details != null ? details : Collections.emptyMap();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
