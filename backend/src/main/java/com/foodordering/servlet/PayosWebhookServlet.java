package com.foodordering.servlet;

import com.foodordering.config.PayosConfig;
import jakarta.servlet.http.HttpServlet;
import com.foodordering.exception.AccountException;
import com.foodordering.integration.PayosClient;
import com.foodordering.service.PayosPaymentService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;

/** A server-to-server endpoint authenticated by the payOS checksum signature. */
@WebServlet(name = "PayosWebhookServlet", urlPatterns = "/api/payos/webhook")
public class PayosWebhookServlet extends HttpServlet {
    private PayosClient client;
    private PayosPaymentService payments;

    public PayosWebhookServlet() {}

    public PayosWebhookServlet(PayosClient client, PayosPaymentService payments) {
        this.client = Objects.requireNonNull(client);
        this.payments = Objects.requireNonNull(payments);
    }

    @Override
    public void init() throws ServletException {
        // Missing payOS credentials must not stop the cash-payment application from starting.
        if (payments == null) payments = new PayosPaymentService();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            byte[] body = request.getInputStream().readNBytes(65537);
            if (body.length > 65536) throw new AccountException("INVALID_REQUEST", "Dữ liệu webhook quá lớn.", 413);
            PayosClient verifier = client == null ? PayosConfig.fromEnvironment().client() : client;
            payments.webhook(verifier.verifyWebhookBody(body));
            response.setStatus(200); response.setContentType("application/json"); response.getWriter().write("{\"code\":\"00\",\"desc\":\"success\"}");
        } catch (Exception failure) {
            response.sendError(ServletSupport.status(failure), ServletSupport.message(failure));
        }
    }
}
