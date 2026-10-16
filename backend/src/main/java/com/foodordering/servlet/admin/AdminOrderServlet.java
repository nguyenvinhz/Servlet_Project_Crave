package com.foodordering.servlet.admin;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.OrderStatus;
import com.foodordering.dao.JpaOrderDAO;
import com.foodordering.dao.JpaPaymentDAO;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.*;
import com.foodordering.servlet.ServletSupport;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(name = "AdminOrderServlet", urlPatterns = "/admin/orders")
public class AdminOrderServlet extends HttpServlet {
    private final CustomerOrderService orders;
    private final PayosPaymentService payments;
    public AdminOrderServlet() { this(new CustomerOrderServiceImpl(new JpaOrderDAO(), new JpaPaymentDAO()), new PayosPaymentService()); }
    public AdminOrderServlet(CustomerOrderService orders) { this(orders, new PayosPaymentService()); }
    public AdminOrderServlet(CustomerOrderService orders, PayosPaymentService payments) { this.orders = orders; this.payments = payments; }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF);
            var all = orders.getAllOrdersForAdmin();
            String status = request.getParameter("status"), search = request.getParameter("search");
            var filtered = all.stream().filter(order -> status == null || status.isBlank() || order.getStatus().name().equals(status))
                    .filter(order -> search == null || search.isBlank() || (order.getOrderId() + " " + order.getReceiverName() + " " + order.getReceiverPhone())
                            .toLowerCase(java.util.Locale.ROOT).contains(search.toLowerCase(java.util.Locale.ROOT))).toList();
            request.setAttribute("orders", filtered);
            request.setAttribute("orderStatuses", OrderStatus.values());
            if (request.getParameter("id") != null) request.setAttribute("selectedOrder", orders.getOrderById(request.getParameter("id")));
        } catch (Exception failure) { request.setAttribute("flashError", ServletSupport.message(failure)); }
        ServletSupport.forward(request, response, "admin/admin-orders");
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String employeeId = SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF).id();
            String id = request.getParameter("orderId");
            OrderStatus status = OrderStatus.valueOf(request.getParameter("status"));
            orders.updateOrderStatus(id, status, employeeId, request.getParameter("note"));
            if (status == OrderStatus.CANCELLED) try { payments.cancelForOrder(id); } catch (RuntimeException unavailable) { log("payOS cancellation pending."); }
            request.getSession().setAttribute("flashMessage", "Đã cập nhật trạng thái đơn hàng.");
        } catch (Exception failure) { request.getSession().setAttribute("flashError", ServletSupport.message(failure)); }
        ServletSupport.redirect(request, response, "/admin/orders");
    }
}
