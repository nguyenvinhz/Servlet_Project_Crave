package com.foodordering.servlet;

import com.foodordering.dto.ApiResponse;
import com.foodordering.utils.JsonUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.foodordering.dto.UpdateOrderStatusRequest;
import com.foodordering.enums.OrderStatus;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.CustomerOrderServiceImpl;
import com.foodordering.repository.CustomerOrderRepositoryImpl;
import com.foodordering.repository.PaymentRepositoryImpl;

@WebServlet(name = "AdminOrderServlet", urlPatterns = "/api/admin/orders/*")
public class AdminOrderServlet extends HttpServlet {

    private CustomerOrderService orderService;

    @Override
    public void init() throws ServletException {
        orderService = new CustomerOrderServiceImpl(new CustomerOrderRepositoryImpl(), new PaymentRepositoryImpl());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                throw new IllegalArgumentException("Thiếu Order ID");
            }
            String orderId = pathInfo.substring(1);
            
            String employeeId = "E001"; // TODO: Lấy từ Admin Auth Session
            
            UpdateOrderStatusRequest updateReq = JsonUtils.readJson(req, UpdateOrderStatusRequest.class);
            OrderStatus status = OrderStatus.valueOf(updateReq.getStatus());
            
            orderService.updateOrderStatus(orderId, status, employeeId, updateReq.getNote());
            
            JsonUtils.writeJson(resp, ApiResponse.success("Cập nhật trạng thái thành công"));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonUtils.writeJson(resp, ApiResponse.error(e.getMessage()));
        }
    }
}

