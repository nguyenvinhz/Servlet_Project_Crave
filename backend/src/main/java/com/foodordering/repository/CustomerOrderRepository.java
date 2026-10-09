package com.foodordering.repository;

import com.foodordering.entity.CustomerOrder;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository {
    CustomerOrder save(CustomerOrder order);
    Optional<CustomerOrder> findById(String id);
    List<CustomerOrder> findByCustomerId(String customerId);
    List<CustomerOrder> findAll();
}
