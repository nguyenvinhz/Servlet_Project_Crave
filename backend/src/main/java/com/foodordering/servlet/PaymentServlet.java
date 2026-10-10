package com.foodordering.servlet;

import com.foodordering.api.BaseApiServlet;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.foodordering.dto.UpdatePaymentStatusRequest;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.service.PaymentService;
import com.foodordering.service.PaymentServiceImpl;
import com.foodordering.repository.PaymentRepositoryImpl;

@WebServlet(name = "PaymentServlet", urlPatterns = "/api/payments/*")
public class PaymentServlet extends BaseApiServlet {

    private PaymentService paymentService;

    @Override
    public void init() throws ServletException {
        paymentService = new PaymentServiceImpl(new PaymentRepositoryImpl());
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
        ProfileResponse user = SessionAuth.requireUser(req);
        if (user.accountType() != AccountType.EMPLOYEE) {
            throw new AccountException("FORBIDDEN", "Chỉ Admin/Nhân viên mới có quyền truy cập", 403);
        }
        return user.id();
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String employeeId = requireEmployee(req);

            String pathInfo = req.getPathInfo();
            if (pathInfo == null || !pathInfo.endsWith("/status")) {
                badRequest(resp, "Invalid endpoint. Expected /{paymentId}/status");
                return;
            }
            
            // Format URL: /api/payments/{paymentId}/status
            String[] segments = pathInfo.substring(1).split("/");
            String paymentId = segments[0];
            
            UpdatePaymentStatusRequest updateReq = readJson(req, UpdatePaymentStatusRequest.class);
            PaymentStatus status = PaymentStatus.valueOf(updateReq.getStatus());
            
            paymentService.updatePaymentStatus(paymentId, status, updateReq.getTransactionRef());
            
            writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success("Cập nhật trạng thái thanh toán thành công"));
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
