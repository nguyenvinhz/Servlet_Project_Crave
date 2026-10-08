package com.foodordering.service;

import com.foodordering.enums.PaymentStatus;

public interface PaymentService {
    void processPayment(String paymentId);
    void updatePaymentStatus(String paymentId, PaymentStatus status, String transactionRef);
}
