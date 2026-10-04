package com.foodordering.dto;

import com.foodordering.utils.JsonProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void successEnvelopeHasDataAndNoError() {
        ApiResponse<Map<String, String>> response = ApiResponse.success(Map.of("status", "UP"));

        assertTrue(response.success());
        assertEquals("UP", response.data().get("status"));
        assertNull(response.error());
    }

    @Test
    void errorEnvelopeUsesStableJsonShape() throws Exception {
        ApiResponse<Void> response = ApiResponse.failure(
                new ApiError("VALIDATION_ERROR", "Invalid input", Map.of("email", "Required")));

        String json = JsonProvider.objectMapper().writeValueAsString(response);

        assertFalse(response.success());
        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"data\":null"));
        assertTrue(json.contains("\"fieldErrors\":{\"email\":\"Required\"}"));
    }
}
