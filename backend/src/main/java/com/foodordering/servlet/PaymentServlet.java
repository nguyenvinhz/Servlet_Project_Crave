package com.foodordering.servlet;

import com.foodordering.dto.ApiResponse;
import com.foodordering.utils.JsonUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import com.foodordering.dto.UpdatePaymentStatusRequest;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.service.PaymentService;
import com.foodordering.service.PaymentServiceImpl;
import com.foodordering.repository.PaymentRepositoryImpl;

@WebServlet(name = "PaymentServlet", urlPatterns = "/api/payments/*")
public class PaymentServlet extends HttpServlet {

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

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String pathInfo = req.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                throw new IllegalArgumentException("Thiếu Payment ID");
            }
            
            String employeeId = (String) req.getSession().getAttribute("employeeId");
            if (employeeId == null) {
                employeeId = req.getParameter("mock_employee");
                if (employeeId == null) {
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    JsonUtils.writeJson(resp, ApiResponse.error("Chỉ Admin/Nhân viên mới có quyền truy cập"));
                    return;
                }
            }

            // Format URL: /api/payments/{paymentId}/status
            String[] segments = pathInfo.substring(1).split("/");
            String paymentId = segments[0];
            
            UpdatePaymentStatusRequest updateReq = JsonUtils.readJson(req, UpdatePaymentStatusRequest.class);
            PaymentStatus status = PaymentStatus.valueOf(updateReq.getStatus());
            
            paymentService.updatePaymentStatus(paymentId, status, updateReq.getTransactionRef());
            
            JsonUtils.writeJson(resp, ApiResponse.success("Cập nhật trạng thái thanh toán thành công"));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonUtils.writeJson(resp, ApiResponse.error(e.getMessage()));
        }
    }
}

