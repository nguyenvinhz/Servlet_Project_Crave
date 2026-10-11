package com.foodordering.servlet;

import com.foodordering.api.BaseApiServlet;
import com.foodordering.dto.ApiResponse;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.security.SessionAuth;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import com.foodordering.dto.OrderResponse;

import com.foodordering.dto.UpdateOrderStatusRequest;
import com.foodordering.enums.OrderStatus;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.CustomerOrderServiceImpl;
import com.foodordering.repository.CustomerOrderRepositoryImpl;
import com.foodordering.repository.PaymentRepositoryImpl;

@WebServlet(name = "AdminOrderServlet", urlPatterns = "/api/admin/orders/*")
public class AdminOrderServlet extends BaseApiServlet {

    private CustomerOrderService orderService;

    public AdminOrderServlet() {
    }

    public AdminOrderServlet(CustomerOrderService orderService) {
        this.orderService = Objects.requireNonNull(orderService);
    }

    @Override
    public void init() throws ServletException {
        if (orderService == null) {
            orderService = new CustomerOrderServiceImpl(new CustomerOrderRepositoryImpl(), new PaymentRepositoryImpl());
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    private String requireEmployee(HttpServletRequest req) {
        return SessionAuth.requireEmployee(req, EmployeeRole.ORDER_STAFF).id();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            requireEmployee(req);
            List<OrderResponse> orders = orderService.getAllOrdersForAdmin();
            writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success(orders));
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String employeeId = requireEmployee(req);

            String pathInfo = req.getPathInfo();
            if (pathInfo == null || !pathInfo.matches("/[^/]+/status")) {
                badRequest(resp, "Invalid endpoint. Expected /{orderId}/status");
                return;
            }
            String orderId = pathInfo.substring(1, pathInfo.length() - "/status".length());
            
            UpdateOrderStatusRequest updateReq = readJson(req, UpdateOrderStatusRequest.class);
            if (updateReq == null || updateReq.getStatus() == null || updateReq.getStatus().isBlank()) {
                badRequest(resp, "Request body must contain an order status.");
                return;
            }
            OrderStatus status = OrderStatus.valueOf(updateReq.getStatus());
            
            orderService.updateOrderStatus(orderId, status, employeeId, updateReq.getNote());
            
            writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success("Cập nhật trạng thái thành công"));
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
