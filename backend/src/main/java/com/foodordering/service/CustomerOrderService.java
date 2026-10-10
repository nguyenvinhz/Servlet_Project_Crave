package com.foodordering.service;

import com.foodordering.dto.OrderRequest;
import com.foodordering.dto.OrderResponse;
import com.foodordering.dto.OrderSummaryResponse;
import com.foodordering.enums.OrderStatus;
import java.util.List;

public interface CustomerOrderService {
    /**
     * Tạo đơn hàng từ giỏ hàng hiện tại của khách.
     *
     * <p><b>Atomic unit (phải rollback toàn bộ nếu bất kỳ bước nào lỗi):</b>
     * <ol>
     *   <li>Đọc subtotal từ v_cart_summary (read-only, cùng EntityManager)</li>
     *   <li>Tạo CustomerOrder</li>
     *   <li>Copy CartItem → OrderDetail (kèm option snapshot)</li>
     *   <li>Tạo Payment với status PENDING</li>
     *   <li>Tạo OrderStatusHistory ban đầu (status PENDING_CONFIRMATION)</li>
     *   <li>Xóa CartItem</li>
     *   <li>Commit</li>
     * </ol>
     *
     * <p><b>Transaction context:</b> Implementation phải truyền cùng một
     * {@code EntityManager} cho tất cả repository thao tác trong phương thức này.
     * Không để mỗi repository tự mở transaction riêng.
     *
     * <p><b>Rollback khi:</b> Giỏ hàng trống, sản phẩm không tồn tại,
     * promotion không hợp lệ, hoặc bất kỳ lỗi database nào.
     *
     * @throws IllegalStateException nếu giỏ hàng trống
     * @throws IllegalArgumentException nếu request thiếu trường bắt buộc
     */
    OrderResponse createOrder(String customerId, OrderRequest request);
    OrderResponse getOrderById(String orderId);
    List<OrderSummaryResponse> getOrdersByCustomer(String customerId);
    void updateOrderStatus(String orderId, OrderStatus status, String employeeId, String note);
}
