package com.foodordering.servlet;

import com.foodordering.dao.CustomerOrderDAO;
import com.foodordering.dao.PaymentDAO;
import com.foodordering.enums.OrderStatus;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.PayosPaymentService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "OrdersPageServlet", urlPatterns = "/orders/*")
public class OrdersPageServlet extends BaseServlet {
    private CustomerOrderService orders;
    private PayosPaymentService payos;
    public OrdersPageServlet() { }
    public OrdersPageServlet(CustomerOrderService orders, PayosPaymentService payos) { this.orders = orders; this.payos = payos; }
    @Override public void init() {
        if (orders == null) orders = new CustomerOrderService(new CustomerOrderDAO(), new PaymentDAO());
        if (payos == null) payos = new PayosPaymentService();
    }
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String path = request.getPathInfo();
            if (path == null || "/".equals(path)) {
                request.setAttribute("orders", orders.getOrdersByCustomer(customerId));
                request.getRequestDispatcher("/WEB-INF/views/order/orders.jsp").forward(request, response);
                return;
            }
            String orderId = path.substring(1);
            var order = orders.getOrderById(orderId);
            if (!customerId.equals(order.getCustomerId())) { response.sendError(403, "Đơn hàng không thuộc tài khoản của bạn."); return; }
            boolean online = order.getPayment() != null && ("BANK_TRANSFER".equals(order.getPayment().getMethod())
                    || "E_WALLET".equals(order.getPayment().getMethod()));
            if (online && order.getStatus() != OrderStatus.CANCELLED
                    && !"SUCCESS".equals(order.getPayment().getStatus()) && !"REFUNDED".equals(order.getPayment().getStatus())) {
                try {
                    request.setAttribute("payos", payos.status(orderId, customerId));
                    order = orders.getOrderById(orderId);
                } catch (RuntimeException unavailable) {
                    boolean expected = unavailable instanceof com.foodordering.exception.AppException;
                    if (!expected) log("Chưa kiểm tra được trạng thái payOS.", unavailable);
                    request.setAttribute("payosError", expected ? unavailable.getMessage()
                            : "Chưa kiểm tra được thanh toán. Vui lòng thử lại.");
                }
            }
            request.setAttribute("order", order);
            request.setAttribute("onlinePayment", online);
            request.getRequestDispatcher("/WEB-INF/views/order/order-detail.jsp").forward(request, response);
        } catch (RuntimeException failure) { handleError(response, failure); }
    }
}
