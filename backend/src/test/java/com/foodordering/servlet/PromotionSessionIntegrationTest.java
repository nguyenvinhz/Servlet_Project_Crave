package com.foodordering.servlet;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.PromotionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionSessionIntegrationTest {
    private final PromotionService promotions = mock(PromotionService.class);
    private final PromotionServlet servlet = new PromotionServlet(promotions);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);

    @BeforeEach
    void setup() {
        Map<String, Object> sessionValues = new HashMap<>();
        Map<String, Object> requestValues = new HashMap<>();
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(anyString())).thenAnswer(call -> sessionValues.get(call.getArgument(0)));
        doAnswer(call -> { sessionValues.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
        when(request.getAttribute(anyString())).thenAnswer(call -> requestValues.get(call.getArgument(0)));
        doAnswer(call -> { requestValues.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(request).setAttribute(anyString(), any());
        SessionAuth.store(session, customer("KH01"));
    }

    @Test
    void aChangedLoginAfterFilterAuthorizationCannotSelectAVoucher() throws Exception {
        SessionAuth.capture(request);
        SessionAuth.store(session, customer("KH02"));

        servlet.doPost(request, response);

        verifyNoInteractions(promotions);
        verify(response).sendRedirect("/crave/auth/login?returnTo=/cart");
        verify(request, never()).getSession();
    }

    @Test
    void logoutAfterFilterAuthorizationCannotSelectAVoucher() throws Exception {
        SessionAuth.capture(request);
        when(request.getSession(false)).thenReturn(null);

        servlet.doPost(request, response);

        verifyNoInteractions(promotions);
        verify(response).sendRedirect("/crave/auth/login?returnTo=/cart");
        verify(request, never()).getSession();
    }

    @Test
    void expiredSessionRedirectsWithoutInvokingPromotionService() throws Exception {
        when(session.getAttribute(anyString())).thenThrow(new IllegalStateException("Expired"));

        servlet.doPost(request, response);

        verifyNoInteractions(promotions);
        verify(response).sendRedirect("/crave/auth/login?returnTo=/cart");
    }

    private ProfileResponse customer(String id) {
        return new ProfileResponse(id, AccountType.CUSTOMER, "Customer", "customer@example.com", "0901234567", null);
    }
}