package com.foodordering.servlet;

import com.foodordering.dao.CustomerOrderDAO;
import com.foodordering.dao.PaymentDAO;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.OrderStatus;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.PayosPaymentService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet(name = "AdminOrderServlet", urlPatterns = "/admin/orders/action")
public class AdminOrderServlet extends BaseServlet {
    private CustomerOrderService orders;
    private PayosPaymentService payos;
    public AdminOrderServlet() { }
    public AdminOrderServlet(CustomerOrderService orders) { this.orders = orders; }
    @Override public void init() {
        if (orders == null) orders = new CustomerOrderService(new CustomerOrderDAO(), new PaymentDAO());
        if (payos == null) payos = new PayosPaymentService();
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String orderId = parameter(request, "orderId");
        try {
            String employeeId = SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF).id();
            OrderStatus status = OrderStatus.valueOf(parameter(request, "status"));
            orders.updateOrderStatus(orderId, status, employeeId, parameter(request, "note"));
            if (status == OrderStatus.CANCELLED) {
                try { payos.cancelForOrder(orderId); }
                catch (RuntimeException unavailable) { log("Chưa đồng bộ được hủy link payOS."); }
            }
            request.getSession().setAttribute("adminOrderMessage", "Đã cập nhật trạng thái đơn hàng.");
        } catch (RuntimeException failure) {
            if (failure instanceof com.foodordering.exception.AppException account
                    && (account.getStatusCode() == 401 || account.getStatusCode() == 403)) {
                response.sendError(account.getStatusCode(), account.getMessage());
                return;
            }
            if (failure instanceof com.foodordering.exception.AppException
                    || failure instanceof com.foodordering.exception.ApiException
                    || failure instanceof IllegalArgumentException)
                request.getSession().setAttribute("adminOrderError", failure instanceof IllegalArgumentException
                        ? "Trạng thái đơn hàng không hợp lệ." : failure.getMessage());
            else { log("Không cập nhật được đơn hàng.", failure); request.getSession().setAttribute("adminOrderError", "Chưa cập nhật được đơn hàng."); }
        }
        redirect(request, response, "/admin/orders?detail=" + URLEncoder.encode(orderId, StandardCharsets.UTF_8));
    }
}
