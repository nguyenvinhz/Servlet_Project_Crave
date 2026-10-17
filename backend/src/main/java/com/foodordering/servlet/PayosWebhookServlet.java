package com.foodordering.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.config.PayosConfig;
import com.foodordering.exception.AppException;
import com.foodordering.gateway.PayosClient;
import com.foodordering.service.PayosPaymentService;
import com.foodordering.gateway.PayosJson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** JSON chỉ dùng tại webhook server-to-server bắt buộc của payOS. */
@WebServlet(name = "PayosWebhookServlet", urlPatterns = "/api/payos/webhook")
public class PayosWebhookServlet extends HttpServlet {
    private PayosClient client;
    private PayosPaymentService payments;
    public PayosWebhookServlet() { }
    public PayosWebhookServlet(PayosClient client, PayosPaymentService payments) { this.client = client; this.payments = payments; }
    @Override public void init() { if (payments == null) payments = new PayosPaymentService(); }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        try {
            byte[] body = request.getInputStream().readNBytes(65537);
            if (body.length > 65536) { response.setStatus(413); response.getWriter().write("{\"success\":false}"); return; }
            JsonNode envelope = PayosJson.objectMapper().readTree(body);
            PayosClient verifier = client == null ? PayosConfig.fromEnvironment().client() : client;
            payments.webhook(verifier.verifyWebhook(envelope));
            response.setStatus(200);
            response.getWriter().write("{\"success\":true}");
        } catch (AppException problem) {
            response.setStatus(problem.getStatusCode());
            response.getWriter().write("{\"success\":false}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            response.setStatus(400);
            response.getWriter().write("{\"success\":false}");
        } catch (RuntimeException unavailable) {
            log("Chưa xử lý được webhook payOS.", unavailable);
            response.setStatus(503);
            response.getWriter().write("{\"success\":false}");
        }
    }
}