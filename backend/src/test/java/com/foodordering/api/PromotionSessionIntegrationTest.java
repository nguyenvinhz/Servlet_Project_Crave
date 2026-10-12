package com.foodordering.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.PromotionService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionSessionIntegrationTest {
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
    void promotionValidationRejectsAnotherLoginAfterFilterAuthorization() throws Exception {
        ProfileResponse otherCustomer = new ProfileResponse("KH02", AccountType.CUSTOMER,
                "Other", "other@example.com", "0901234568", null);
        ProfileResponse employee = new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234569", EmployeeRole.ADMIN);
        for (ProfileResponse next : new ProfileResponse[]{CUSTOMER, otherCustomer, employee}) {
            Exchange exchange = request("{\"code\":\"SAVE10\",\"subtotal\":150000}");
            SessionAuth.capture(exchange.request);
            SessionAuth.store(exchange.session, next);
            when(exchange.request.getHeader("X-Customer-Id")).thenReturn("KH99");
            when(exchange.request.getParameter("customerId")).thenReturn("KH99");

            servlet.doPost(exchange.request, exchange.response);

            exchange.assertError(401, "UNAUTHORIZED");
            assertEquals(next, exchange.session.getAttribute(SessionAuth.CURRENT_USER));
            verify(exchange.request, never()).getInputStream();
            verify(exchange.session, never()).invalidate();
        }
        verifyNoInteractions(promotions);
    }

    @Test
    void promotionValidationRejectsLogoutAfterFilterAuthorization() throws Exception {
        Exchange exchange = request("{\"code\":\"SAVE10\"}");
        SessionAuth.capture(exchange.request);
        when(exchange.request.getSession(false)).thenReturn(null);

        servlet.doPost(exchange.request, exchange.response);

        exchange.assertError(401, "UNAUTHORIZED");
        verify(exchange.request, never()).getSession(true);
        verifyNoInteractions(promotions);
    }

    @Test
    void expiredPromotionSessionReturns401WithoutInvokingService() throws Exception {
        Exchange exchange = request("{\"code\":\"SAVE10\"}");
        when(exchange.session.getAttribute(anyString())).thenThrow(new IllegalStateException("Session expired"));

        servlet.doPost(exchange.request, exchange.response);

        exchange.assertError(401, "UNAUTHORIZED");
        verifyNoInteractions(promotions);
    }

    @Test
    void nullPromotionValidationBodyReturns400WithoutInvokingService() throws Exception {
        Exchange exchange = request("null");

        servlet.doPost(exchange.request, exchange.response);

        exchange.assertError(400, "INVALID_REQUEST");
        verifyNoInteractions(promotions);
    }

    @Test
    void promotionValidationRejectsClientIdentityInJsonBody() throws Exception {
        Exchange exchange = request("{\"code\":\"SAVE10\",\"subtotal\":150000,\"customerId\":\"KH99\"}");

        servlet.doPost(exchange.request, exchange.response);

        exchange.assertError(400, "INVALID_REQUEST");
        verifyNoInteractions(promotions);
    }

    private Exchange request(String payload) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(SessionAuth.CURRENT_USER, CUSTOMER);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(anyString())).thenAnswer(call -> sessionAttributes.get(call.getArgument(0)));
        doAnswer(call -> {
            sessionAttributes.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        doAnswer(call -> {
            sessionAttributes.remove(call.getArgument(0));
            return null;
        }).when(session).removeAttribute(anyString());
        Map<String, Object> requestAttributes = new HashMap<>();
        when(request.getAttribute(anyString())).thenAnswer(call -> requestAttributes.get(call.getArgument(0)));
        doAnswer(call -> {
            requestAttributes.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(request).setAttribute(anyString(), any());
        ByteArrayInputStream bytes = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
            @Override public int read() { return bytes.read(); }
        });
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return new Exchange(request, response, session, body);
    }

    private record Exchange(HttpServletRequest request, HttpServletResponse response, HttpSession session,
                            StringWriter body) {
        void assertError(int status, String code) throws Exception {
            verify(response).setStatus(status);
            verify(response).setContentType("application/json");
            JsonNode json = JsonProvider.objectMapper().readTree(body.toString());
            assertFalse(json.path("success").asBoolean());
            assertEquals(code, json.path("error").path("code").asText());
        }
    }
}
