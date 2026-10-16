package com.foodordering.servlet.admin;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.dao.JpaPaymentDAO;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.PaymentService;
import com.foodordering.service.PaymentServiceImpl;
import com.foodordering.servlet.ServletSupport;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(name = "AdminPaymentServlet", urlPatterns = "/admin/payments")
public class AdminPaymentServlet extends HttpServlet {
    private final PaymentService payments;
    public AdminPaymentServlet() { this(new PaymentServiceImpl(new JpaPaymentDAO())); }
    public AdminPaymentServlet(PaymentService payments) { this.payments = payments; }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF);
            payments.updatePaymentStatus(request.getParameter("paymentId"), PaymentStatus.valueOf(request.getParameter("status")), request.getParameter("transactionRef"));
            request.getSession().setAttribute("flashMessage", "Đã cập nhật thanh toán.");
        } catch (Exception failure) { request.getSession().setAttribute("flashError", ServletSupport.message(failure)); }
        ServletSupport.redirect(request, response, "/admin/orders");
    }
}
