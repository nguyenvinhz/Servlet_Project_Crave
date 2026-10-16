package com.foodordering.dao;

import com.foodordering.model.Payment;
import java.util.Optional;

public interface PaymentDAO {
    Payment save(Payment payment);
    Optional<Payment> findById(String id);
    Optional<Payment> findByOrderId(String orderId);
}
