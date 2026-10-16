package com.foodordering.dao;

import com.foodordering.model.CustomerOrder;
import java.util.List;
import java.util.Optional;

public interface OrderDAO {
    CustomerOrder save(CustomerOrder order);
    Optional<CustomerOrder> findById(String id);
    List<CustomerOrder> findByCustomerId(String customerId);
    List<CustomerOrder> findAll();
}
