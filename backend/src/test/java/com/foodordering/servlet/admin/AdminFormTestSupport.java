package com.foodordering.servlet.admin;

import com.foodordering.bean.ProfileBean;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

abstract class AdminFormTestSupport {
    HttpServletRequest request;
    HttpServletResponse response;
    HttpSession session;
    RequestDispatcher dispatcher;
    Map<String, String> parameters;
    Map<String, Object> attributes;
    Map<String, Object> sessionAttributes;

    @BeforeEach void setupRequest() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);
        parameters = new HashMap<>();
        attributes = new HashMap<>();
        sessionAttributes = new HashMap<>();
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter(anyString())).thenAnswer(call -> parameters.get(call.getArgument(0)));
        when(request.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> { attributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(request).setAttribute(anyString(), any());
        when(session.getAttribute(anyString())).thenAnswer(call -> sessionAttributes.get(call.getArgument(0)));
        doAnswer(call -> { sessionAttributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
        doAnswer(call -> { sessionAttributes.remove(call.getArgument(0)); return null; })
                .when(session).removeAttribute(anyString());
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        signIn(EmployeeRole.ADMIN);
    }

    void signIn(EmployeeRole role) {
        sessionAttributes.put("currentUser", new ProfileBean("NV01", AccountType.EMPLOYEE, "Quản lý",
                "manager@example.com", "0900000001", role));
    }
}
