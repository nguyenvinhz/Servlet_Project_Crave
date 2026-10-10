package com.foodordering.exception;

import java.util.Map;

public class AccountException extends AppException {

    public AccountException(String errorCode, String message, int statusCode) {
        super(errorCode, message, statusCode);
    }

    public AccountException(String errorCode, String message, int statusCode, Map<String, String> details) {
        super(errorCode, message, statusCode, details);
    }
}
