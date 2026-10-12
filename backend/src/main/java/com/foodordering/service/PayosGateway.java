package com.foodordering.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface PayosGateway {
    record PaymentLink(long orderCode, long amount, String paymentLinkId, String checkoutUrl, String status, Long amountPaid) {
        public PaymentLink(long orderCode, long amount, String paymentLinkId, String checkoutUrl, String status) {
            this(orderCode, amount, paymentLinkId, checkoutUrl, status, null);
        }
    }
    record PaymentState(long orderCode, long amount, long amountPaid, String paymentLinkId, String status, String reference) { }
    record WebhookPayment(long orderCode, long amount, String paymentLinkId, String reference, boolean successful) { }
    void requireConfigured();
    PaymentLink createOrGet(long orderCode, long amount, String orderId, boolean retry);
    PaymentState getPayment(long orderCode);
    WebhookPayment verifyWebhook(JsonNode webhook);
}
