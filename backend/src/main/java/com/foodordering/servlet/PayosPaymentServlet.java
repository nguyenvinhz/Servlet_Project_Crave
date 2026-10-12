package com.foodordering.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.api.BaseApiServlet;
import com.foodordering.dto.ApiResponse;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.PayosPaymentService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "PayosPaymentServlet", urlPatterns = "/api/payments/payos/*")
public class PayosPaymentServlet extends BaseApiServlet {
    private PayosPaymentService payments;
    public PayosPaymentServlet() { }
    public PayosPaymentServlet(PayosPaymentService payments) { this.payments = payments; }
    @Override public void init() { if (payments == null) payments = new PayosPaymentService(); }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            String path = request.getPathInfo();
            if ("/webhook".equals(path)) {
                if (request.getContentLengthLong() > 65536) { badRequest(response, "Dữ liệu webhook quá lớn."); return; }
                byte[] body = request.getInputStream().readNBytes(65537);
                if (body.length > 65536) { badRequest(response, "Dữ liệu webhook quá lớn."); return; }
                JsonNode payload = JsonProvider.objectMapper().readTree(body);
                payments.receiveWebhook(payload);
                writeJson(response, 200, ApiResponse.success("Đã nhận thông tin thanh toán."));
                return;
            }
            String customerId = SessionAuth.requireCustomer(request).id();
            if (path == null || !path.matches("/orders/[A-Za-z0-9]{1,10}/(link|sync)")) { badRequest(response, "Đường dẫn thanh toán không hợp lệ."); return; }
            String[] segments = path.split("/");
            Object result = "link".equals(segments[3]) ? payments.createLink(customerId, segments[2]) : payments.syncPayment(customerId, segments[2]);
            writeJson(response, 200, ApiResponse.success(result));
        } catch (Exception exception) {
            if (exception instanceof com.foodordering.exception.PaymentGatewayException gatewayException
                    && gatewayException.getRetryAfterSeconds() > 0) {
                response.setHeader("Retry-After", String.valueOf(gatewayException.getRetryAfterSeconds()));
            }
            handleError(response, exception);
        }
    }
}
