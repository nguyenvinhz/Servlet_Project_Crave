package com.foodordering.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.config.PayosConfig;
import com.foodordering.dto.ApiResponse;
import com.foodordering.exception.AccountException;
import com.foodordering.gateway.PayosClient;
import com.foodordering.service.PayosPaymentService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;

/** A server-to-server endpoint authenticated by the payOS checksum signature. */
@WebServlet(name = "payosWebhookServlet", urlPatterns = "/api/payos/webhook")
public class PayosWebhookServlet extends BaseApiServlet {
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
            JsonNode envelope = JsonProvider.objectMapper().readTree(body);
            PayosClient verifier = client == null ? PayosConfig.fromEnvironment().client() : client;
            payments.webhook(verifier.verifyWebhook(envelope));
            writeJson(response, 200, ApiResponse.success("Đã nhận thông tin thanh toán."));
        } catch (Exception failure) {
            handleError(response, failure);
        }
    }
}
