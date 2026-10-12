package com.foodordering.exception;

public class PaymentGatewayException extends AppException {
    private final int retryAfterSeconds;
    public PaymentGatewayException(String code, String message, int status) {
        this(code, message, status, 0);
    }
    public PaymentGatewayException(String code, String message, int status, int retryAfterSeconds) {
        super(code, message, status);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}
