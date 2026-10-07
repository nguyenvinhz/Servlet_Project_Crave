package com.foodordering.service;

import com.foodordering.dto.OrderRequest;
import com.foodordering.dto.OrderResponse;
import com.foodordering.dto.OrderSummaryResponse;
import com.foodordering.enums.OrderStatus;
import java.util.List;

public interface CustomerOrderService {
    OrderResponse createOrder(String customerId, OrderRequest request);
    OrderResponse getOrderById(String orderId);
    List<OrderSummaryResponse> getOrdersByCustomer(String customerId);
    void updateOrderStatus(String orderId, OrderStatus status, String employeeId, String note);
}
