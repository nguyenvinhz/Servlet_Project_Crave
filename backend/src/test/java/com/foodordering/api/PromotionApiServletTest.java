package com.foodordering.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.service.PromotionService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PromotionApiServletTest {
    private static final ProfileResponse CUSTOMER = new ProfileResponse("KH01", AccountType.CUSTOMER,
            "Customer", "customer@example.com", "0901234567", null);
    private PromotionService promotions;
    private PromotionApiServlet servlet;

    @BeforeEach
    void setUp() {
        promotions = mock(PromotionService.class);
        servlet = new PromotionApiServlet(promotions);
    }

    @Test
    void publicListNeedsNoSession() throws Exception {
        when(promotions.getActivePromotions()).thenReturn(List.of());
        Exchange exchange = request(null, null);

        servlet.doGet(exchange.request, exchange.response);

        verify(exchange.response).setStatus(200);
        assertTrue(exchange.json().path("success").asBoolean());
        assertTrue(exchange.json().path("data").isArray());
        verify(exchange.request, never()).getSession();
        verify(exchange.request, never()).getSession(anyBoolean());
    }

    @Test
    void validationRequiresCustomerPrincipalAndDoesNotCreateGuestSessions() throws Exception {
        ProfileResponse employee = new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234567", EmployeeRole.ADMIN);
        for (ProfileResponse principal : new ProfileResponse[]{null, employee}) {
            Exchange exchange = request(principal, "{\"code\":\"SAVE10\"}");
            when(exchange.request.getHeader("X-Customer-Id")).thenReturn("KH99");
            when(exchange.request.getParameter("customerId")).thenReturn("KH99");

            servlet.doPost(exchange.request, exchange.response);

            exchange.assertError(principal == null ? 401 : 403, principal == null ? "UNAUTHORIZED" : "FORBIDDEN");
            verify(exchange.request, never()).getSession();
            verify(exchange.request, never()).getSession(true);
        }
        verifyNoInteractions(promotions);
    }

    @Test
    void validationUsesAccountPrincipalWithoutNeedingLegacyCustomerId() throws Exception {
        PromotionValidationResultDto valid = new PromotionValidationResultDto();
        valid.setValid(true);
        when(promotions.validatePromotion(any(ValidatePromotionRequest.class), eq("KH01"))).thenReturn(valid);
        Exchange exchange = request(CUSTOMER, "{\"code\":\"SAVE10\",\"subtotal\":150000}");
        when(exchange.session.getAttribute("customerId")).thenReturn("KH99");

        servlet.doPost(exchange.request, exchange.response);

        verify(exchange.response).setStatus(200);
        assertTrue(exchange.json().path("data").path("valid").asBoolean());
        ArgumentCaptor<ValidatePromotionRequest> body = ArgumentCaptor.forClass(ValidatePromotionRequest.class);
        verify(promotions).validatePromotion(body.capture(), eq("KH01"));
        assertEquals("SAVE10", body.getValue().getCode());
    }

    @Test
    void malformedJsonReturnsCanonical400Envelope() throws Exception {
        Exchange exchange = request(CUSTOMER, "{invalid");

        servlet.doPost(exchange.request, exchange.response);

        exchange.assertError(400, "INVALID_REQUEST");
        verifyNoInteractions(promotions);
    }

    @Test
    void inapplicableVoucherKeepsDomainErrorCodeInEnvelope() throws Exception {
        when(promotions.validatePromotion(any(ValidatePromotionRequest.class), eq("KH01")))
                .thenReturn(new PromotionValidationResultDto(false, "Voucher expired", "PROMOTION_EXPIRED"));
        Exchange exchange = request(CUSTOMER, "{\"code\":\"SAVE10\"}");

        servlet.doPost(exchange.request, exchange.response);

        exchange.assertError(400, "PROMOTION_EXPIRED");
        assertEquals("Voucher expired", exchange.json().path("error").path("message").asText());
    }

    @Test
    void missingPromotionReturnsJson404() throws Exception {
        Exchange exchange = request(null, null);
        when(exchange.request.getParameter("code")).thenReturn("UNKNOWN");

        servlet.doGet(exchange.request, exchange.response);

        exchange.assertError(404, "PROMOTION_NOT_FOUND");
    }

    @Test
    void serviceFailuresReturnSanitizedJsonInsteadOfContainerErrorPage() throws Exception {
        when(promotions.getActivePromotions()).thenThrow(new IllegalStateException("jdbc:mysql://private-host secret"));
        Exchange exchange = request(null, null);

        servlet.doGet(exchange.request, exchange.response);

        exchange.assertError(500, "INTERNAL_SERVER_ERROR");
        assertFalse(exchange.body.toString().contains("private-host"));
        assertFalse(exchange.body.toString().contains("secret"));
    }

    private Exchange request(ProfileResponse principal, String body) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = principal == null ? null : mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        if (session != null) {
            when(session.getAttribute("currentUser")).thenReturn(principal);
        }
        if (body != null) {
            ByteArrayInputStream bytes = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            when(request.getInputStream()).thenReturn(new ServletInputStream() {
                @Override public int read() { return bytes.read(); }
                @Override public boolean isFinished() { return bytes.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            });
        }
        StringWriter output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        return new Exchange(request, response, session, output);
    }

    private record Exchange(HttpServletRequest request, HttpServletResponse response, HttpSession session,
                            StringWriter body) {
        JsonNode json() throws Exception {
            return JsonProvider.objectMapper().readTree(body.toString());
        }

        void assertError(int status, String code) throws Exception {
            verify(response).setStatus(status);
            verify(response).setContentType("application/json");
            assertFalse(json().path("success").asBoolean());
            assertEquals(code, json().path("error").path("code").asText());
        }
    }
}
