package com.foodordering.enums;

/**
 * Trạng thái đơn hàng của khách hàng theo schema bảng customer_order.
 */
public enum OrderStatus {
    PENDING_CONFIRMATION,
    PREPARING,
    DELIVERING,
    COMPLETED,
    CANCELLED
}
