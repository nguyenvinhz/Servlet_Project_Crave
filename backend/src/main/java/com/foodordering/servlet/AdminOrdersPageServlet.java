package com.foodordering.servlet;

import com.foodordering.dao.CustomerOrderDAO;
import com.foodordering.dao.PaymentDAO;
import com.foodordering.dto.OrderResponse;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.OrderStatus;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CustomerOrderService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "AdminOrdersPageServlet", urlPatterns = "/admin/orders")
public class AdminOrdersPageServlet extends BaseServlet {
    private CustomerOrderService orders;
    @Override public void init() { orders = new CustomerOrderService(new CustomerOrderDAO(), new PaymentDAO()); }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF);
            var all = orders.getAllOrdersForAdmin();
            request.setAttribute("orders", all);
            request.setAttribute("orderTotal", all.size());
            request.setAttribute("orderPending", all.stream().filter(o -> o.getStatus() == OrderStatus.PENDING_CONFIRMATION).count());
            request.setAttribute("orderCompleted", all.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count());
            request.setAttribute("orderProcessing", all.stream().filter(o -> o.getStatus() == OrderStatus.PREPARING || o.getStatus() == OrderStatus.DELIVERING).count());
            String selected = parameter(request, "detail");
            OrderResponse detail = all.stream().filter(o -> o.getOrderId().equals(selected)).findFirst().orElse(null);
            request.setAttribute("detailOrder", detail);
            request.getRequestDispatcher("/WEB-INF/views/admin/admin-orders.jsp").forward(request, response);
        } catch (RuntimeException failure) { handleError(response, failure); }
    }
}