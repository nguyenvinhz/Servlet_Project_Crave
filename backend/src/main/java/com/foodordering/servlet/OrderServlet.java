package com.foodordering.servlet;

import com.foodordering.api.BaseApiServlet;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.security.SessionAuth;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import com.foodordering.dto.OrderRequest;
import com.foodordering.dto.OrderResponse;
import com.foodordering.dto.OrderSummaryResponse;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.CustomerOrderServiceImpl;
import com.foodordering.repository.CustomerOrderRepositoryImpl;
import com.foodordering.repository.PaymentRepositoryImpl;

@WebServlet(name = "OrderServlet", urlPatterns = "/api/orders/*")
public class OrderServlet extends BaseApiServlet {

    private CustomerOrderService orderService;

    @Override
    public void init() throws ServletException {
        orderService = new CustomerOrderServiceImpl(new CustomerOrderRepositoryImpl(), new PaymentRepositoryImpl());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            ProfileResponse customer = SessionAuth.requireCustomer(req);
            String customerId = customer.id();
            
            // Xử lý route chi tiết đơn hàng /api/orders/{id} hoặc danh sách
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.length() > 1) {
                String orderId = pathInfo.substring(1);
                OrderResponse order = orderService.getOrderById(orderId);
                if (!order.getCustomerId().equals(customerId)) {
                    writeJson(resp, HttpServletResponse.SC_FORBIDDEN, ApiResponse.error("Bạn không có quyền xem đơn hàng này", "FORBIDDEN"));
                    return;
                }
                writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success(order));
            } else {
                List<OrderSummaryResponse> orders = orderService.getOrdersByCustomer(customerId);
                writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success(orders));
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            ProfileResponse customer = SessionAuth.requireCustomer(req);
            String customerId = customer.id();

            OrderRequest request = readJson(req, OrderRequest.class);
            OrderResponse response = orderService.createOrder(customerId, request);
            
            writeJson(resp, HttpServletResponse.SC_CREATED, ApiResponse.success(response));
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
