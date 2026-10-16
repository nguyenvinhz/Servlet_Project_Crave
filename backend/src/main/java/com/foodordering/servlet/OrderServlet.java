package com.foodordering.servlet;

import com.foodordering.bean.OrderBean;
import com.foodordering.exception.AccountException;
import com.foodordering.exception.AppException;
import com.foodordering.exception.BusinessException;
import com.foodordering.dao.JpaOrderDAO;
import com.foodordering.dao.JpaPaymentDAO;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.CustomerOrderServiceImpl;
import com.foodordering.service.PayosPaymentService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(name = "OrderServlet", urlPatterns = {"/orders", "/orders/*"})
public class OrderServlet extends HttpServlet {
    private final CustomerOrderService orders;
    private final PayosPaymentService payments;
    public OrderServlet() {
        this(new CustomerOrderServiceImpl(new JpaOrderDAO(), new JpaPaymentDAO()), new PayosPaymentService());
    }
    public OrderServlet(CustomerOrderService orders) { this(orders, new PayosPaymentService()); }
    public OrderServlet(CustomerOrderService orders, PayosPaymentService payments) { this.orders = orders; this.payments = payments; }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String path = request.getPathInfo();
            if (path == null || path.equals("/")) {
                request.setAttribute("orders", orders.getOrdersByCustomer(customerId));
                ServletSupport.forward(request, response, "order/orders");
            } else {
                String id = orderId(path);
                var order = owned(id, customerId);
                if (order.getPayment() != null && !"CASH".equals(order.getPayment().getMethod())) {
                    try { request.setAttribute("paymentLink", payments.status(id, customerId)); order = owned(id, customerId); }
                    catch (Exception unavailable) { request.setAttribute("paymentMessage", ServletSupport.message(unavailable)); }
                }
                request.setAttribute("order", order);
                ServletSupport.forward(request, response, "order/order-detail");
            }
        } catch (AppException | BusinessException failure) { response.sendError(ServletSupport.status(failure), failure.getMessage()); }
        catch (Exception failure) { request.setAttribute("flashError", ServletSupport.message(failure));
            ServletSupport.forward(request, response, "order/orders"); }
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String id = null;
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String path = request.getPathInfo();
            if (path == null || !path.matches("/[A-Za-z0-9_-]{1,40}/cancel")) { response.sendError(404); return; }
            id = path.split("/")[1];
            orders.cancelOrder(id, customerId, request.getParameter("reason"));
            try { payments.cancelForOrder(id); } catch (RuntimeException unavailable) { log("payOS cancellation will retry on status check."); }
            request.getSession().setAttribute("flashMessage", "Đã hủy đơn hàng thành công.");
        } catch (Exception failure) { request.getSession().setAttribute("flashError", ServletSupport.message(failure)); }
        ServletSupport.redirect(request, response, id == null ? "/orders" : "/orders/" + id);
    }
    private OrderBean owned(String id, String customerId) {
        OrderBean order = orders.getOrderById(id);
        if (!customerId.equals(order.getCustomerId())) throw new AccountException("FORBIDDEN", "Bạn không có quyền xem đơn hàng này.", 403);
        return order;
    }
    private static String orderId(String path) {
        if (!path.matches("/[A-Za-z0-9_-]{1,40}")) throw new AccountException("NOT_FOUND", "Không tìm thấy đơn hàng.", 404);
        return path.substring(1);
    }
}
