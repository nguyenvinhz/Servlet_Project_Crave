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
import com.foodordering.dto.OrderResponse;

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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String employeeId = (String) req.getSession().getAttribute("employeeId");
            if (employeeId == null) {
                employeeId = req.getParameter("mock_employee");
                if (employeeId == null) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    JsonUtils.writeJson(resp, ApiResponse.error("Chỉ Admin/Nhân viên mới có quyền truy cập"));
                    return;
                }
            }
            List<OrderResponse> orders = orderService.getAllOrdersForAdmin();
            JsonUtils.writeJson(resp, ApiResponse.success(orders));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonUtils.writeJson(resp, ApiResponse.error(e.getMessage()));
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                throw new IllegalArgumentException("Thiếu Order ID");
            }
            String orderId = pathInfo.substring(1);
            
            String employeeId = (String) req.getSession().getAttribute("employeeId");
            if (employeeId == null) {
                employeeId = req.getParameter("mock_employee");
                if (employeeId == null) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    JsonUtils.writeJson(resp, ApiResponse.error("Chỉ Admin/Nhân viên mới có quyền truy cập"));
                    return;
                }
            }
            
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

