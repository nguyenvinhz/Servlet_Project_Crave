package com.foodordering.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderResponseSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldSerializeOrderResponseCorrectly() throws Exception {
        OrderResponse.OrderItemResponse item = new OrderResponse.OrderItemResponse();
        item.setFoodId("FOOD-1");
        item.setFoodName("Burger");
        item.setLineTotal(new BigDecimal("50000"));

        OrderResponse.PaymentSummary payment = new OrderResponse.PaymentSummary();
        payment.setStatus("SUCCESS");

        OrderResponse.StatusHistoryEntry history = new OrderResponse.StatusHistoryEntry();
        history.setStatus("PENDING_CONFIRMATION");

        OrderResponse response = new OrderResponse();
        response.setOrderId("ORD-123");
        response.setItems(List.of(item));
        response.setPayment(payment);
        response.setHistory(List.of(history));

        String json = objectMapper.writeValueAsString(response);

        // Verify keys exist
        assertTrue(json.contains("\"orderId\""));
        assertTrue(json.contains("\"items\""));
        assertTrue(json.contains("\"payment\""));
        assertTrue(json.contains("\"history\""));
        
        // Verify item keys
        assertTrue(json.contains("\"foodId\""));
        assertTrue(json.contains("\"foodName\""));
        assertTrue(json.contains("\"lineTotal\""));
    }

    @Test
    void shouldSerializeApiErrorWithFieldErrors() throws Exception {
        ApiError error = new ApiError("400", "Bad Request", Map.of("email", "Invalid email"));
        String json = objectMapper.writeValueAsString(error);
        
        // Must contain fieldErrors, NOT details
        assertTrue(json.contains("\"fieldErrors\""));
        assertTrue(!json.contains("\"details\""));
    }
}
