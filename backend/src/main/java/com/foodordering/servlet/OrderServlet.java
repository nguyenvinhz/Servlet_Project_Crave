package com.foodordering.servlet;

import com.foodordering.dto.ApiResponse;
import com.foodordering.utils.JsonUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import com.foodordering.dto.OrderRequest;
import com.foodordering.dto.OrderResponse;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.CustomerOrderServiceImpl;
import com.foodordering.repository.CustomerOrderRepositoryImpl;
import com.foodordering.repository.PaymentRepositoryImpl;

@WebServlet(name = "OrderServlet", urlPatterns = "/api/orders/*")
public class OrderServlet extends HttpServlet {

    private CustomerOrderService orderService;

    @Override
    public void init() throws ServletException {
        orderService = new CustomerOrderServiceImpl(new CustomerOrderRepositoryImpl(), new PaymentRepositoryImpl());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            // Lấy customerId từ Auth Filter (Ngày 3)
            Object sessionUser = req.getSession().getAttribute("userId");
            String customerId = sessionUser != null ? sessionUser.toString() : "C001";
            
            // Xử lý route chi tiết đơn hàng /api/orders/{id} hoặc danh sách
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.length() > 1) {
                String orderId = pathInfo.substring(1);
                OrderResponse order = orderService.getOrderById(orderId);
                JsonUtils.writeJson(resp, ApiResponse.success(order));
            } else {
                List<OrderResponse> orders = orderService.getOrdersByCustomer(customerId);
                JsonUtils.writeJson(resp, ApiResponse.success(orders));
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonUtils.writeJson(resp, ApiResponse.error(e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Object sessionUser = req.getSession().getAttribute("userId");
            String customerId = sessionUser != null ? sessionUser.toString() : "C001";
            
            OrderRequest request = JsonUtils.readJson(req, OrderRequest.class);
            OrderResponse response = orderService.createOrder(customerId, request);
            
            resp.setStatus(HttpServletResponse.SC_CREATED);
            JsonUtils.writeJson(resp, ApiResponse.success(response));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonUtils.writeJson(resp, ApiResponse.error(e.getMessage()));
        }
    }
}
