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
import java.util.Objects;

import com.foodordering.dto.UpdatePaymentStatusRequest;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.service.PaymentService;
import com.foodordering.service.PaymentServiceImpl;
import com.foodordering.repository.PaymentRepositoryImpl;

@WebServlet(name = "PaymentServlet", urlPatterns = "/api/admin/payments/*")
public class PaymentServlet extends BaseApiServlet {

    private PaymentService paymentService;

    public PaymentServlet() {
    }

    public PaymentServlet(PaymentService paymentService) {
        this.paymentService = Objects.requireNonNull(paymentService);
    }

    @Override
    public void init() throws ServletException {
        if (paymentService == null) {
            paymentService = new PaymentServiceImpl(new PaymentRepositoryImpl());
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

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            SessionAuth.requireEmployee(req, EmployeeRole.ORDER_STAFF);

            String pathInfo = req.getPathInfo();
            if (pathInfo == null || !pathInfo.matches("/[^/]+/status")) {
                badRequest(resp, "Đường dẫn cập nhật thanh toán không hợp lệ.");
                return;
            }
            
            // Format URL: /api/admin/payments/{paymentId}/status
            String[] segments = pathInfo.substring(1).split("/");
            String paymentId = segments[0];
            
            UpdatePaymentStatusRequest updateReq = readJson(req, UpdatePaymentStatusRequest.class);
            if (updateReq == null || updateReq.getStatus() == null || updateReq.getStatus().isBlank()) {
                badRequest(resp, "Vui lòng chọn trạng thái thanh toán.");
                return;
            }
            PaymentStatus status;
            try { status = PaymentStatus.valueOf(updateReq.getStatus()); }
            catch (IllegalArgumentException invalid) { badRequest(resp, "Trạng thái thanh toán không hợp lệ."); return; }
            
            paymentService.updatePaymentStatus(paymentId, status, updateReq.getTransactionRef());
            
            writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success("Cập nhật trạng thái thanh toán thành công"));
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
