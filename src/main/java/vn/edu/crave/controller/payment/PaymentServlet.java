package vn.edu.crave.controller.payment;

import vn.edu.crave.controller.BaseServlet;

import vn.edu.crave.model.dao.PaymentDAO;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.PaymentStatus;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.PaymentService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet(name = "PaymentServlet", urlPatterns = "/admin/orders/payment")
public class PaymentServlet extends BaseServlet {
    private PaymentService payments;
    public PaymentServlet() { }
    public PaymentServlet(PaymentService payments) { this.payments = payments; }
    @Override public void init() { if (payments == null) payments = new PaymentService(new PaymentDAO()); }
    @Override public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF);
            payments.updatePaymentStatus(parameter(request, "paymentId"),
                    PaymentStatus.valueOf(parameter(request, "status")), parameter(request, "transactionRef"));
            request.getSession().setAttribute("adminOrderMessage", "Đã cập nhật thanh toán.");
        } catch (RuntimeException failure) {
            if (failure instanceof vn.edu.crave.exception.AppException account
                    && (account.getStatusCode() == 401 || account.getStatusCode() == 403)) {
                response.sendError(account.getStatusCode(), account.getMessage());
                return;
            }
            if (failure instanceof vn.edu.crave.exception.AppException
                    || failure instanceof vn.edu.crave.exception.ApiException
                    || failure instanceof IllegalArgumentException)
                request.getSession().setAttribute("adminOrderError", failure instanceof IllegalArgumentException
                        ? "Trạng thái thanh toán không hợp lệ." : failure.getMessage());
            else { log("Không cập nhật được thanh toán.", failure); request.getSession().setAttribute("adminOrderError", "Chưa cập nhật được thanh toán."); }
        }
        redirect(request, response, "/admin/orders?detail="
                + URLEncoder.encode(parameter(request, "orderId"), StandardCharsets.UTF_8));
    }
}
