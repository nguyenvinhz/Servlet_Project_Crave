package com.foodordering.exception;

import jakarta.servlet.http.HttpServletResponse;

public class BusinessRuleException extends AppException {

    public BusinessRuleException(String message) {
        super("BUSINESS_RULE_VIOLATION", message, HttpServletResponse.SC_BAD_REQUEST);
    }
}
