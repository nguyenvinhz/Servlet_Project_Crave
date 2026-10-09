package com.foodordering.api;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.config.DatabaseContextListener;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.ProfileUpdateRequest;
import com.foodordering.enums.AccountType;
import com.foodordering.service.AccountService;
import com.foodordering.servlet.AccountPageServlet;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormApiContractTest {

    @Test
    void profileAcceptsBothPutAndPost() throws Exception {
        AccountService service = mock(AccountService.class);
        ProfileResponse customer = new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Nguyễn Quang Vinh", "vinh@example.com", "0901234567", null);
        when(service.updateProfile(eq("KH01"), any(ProfileUpdateRequest.class))).thenReturn(customer);
        ProfileApiServlet servlet = new ProfileApiServlet(service);

        for (String methodName : new String[]{"doPut", "doPost"}) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            HttpSession session = mock(HttpSession.class);
            StringWriter body = new StringWriter();
            when(request.getSession(false)).thenReturn(session);
            when(request.getContentType()).thenReturn("application/json");
            when(session.getAttribute("currentUser")).thenReturn(customer);
            when(request.getReader()).thenReturn(new BufferedReader(new StringReader(
                    "{\"fullName\":\"Nguyễn Quang Vinh\",\"email\":\"vinh@example.com\",\"phone\":\"0901234567\"}")));
            when(request.getInputStream()).thenReturn(AccountApiServletTest.jsonStream(
                    "{\"fullName\":\"Nguyễn Quang Vinh\",\"email\":\"vinh@example.com\",\"phone\":\"0901234567\"}"));
            when(response.getWriter()).thenReturn(new PrintWriter(body));

            Method method = ProfileApiServlet.class.getDeclaredMethod(methodName,
                    HttpServletRequest.class, HttpServletResponse.class);
            method.setAccessible(true);
            method.invoke(servlet, request, response);

            verify(response).setStatus(HttpServletResponse.SC_OK);
            assertTrue(body.toString().contains("\"success\":true"));
            assertTrue(body.toString().contains("\"id\":\"KH01\""));
        }
    }

    @Test
    void healthReportsDatabaseUpAndDown() throws Exception {
        HealthApiServlet servlet = new HealthApiServlet();
        ServletConfig config = mock(ServletConfig.class);
        ServletContext context = mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        servlet.init(config);

        Method method = HealthApiServlet.class.getDeclaredMethod("doGet",
                HttpServletRequest.class, HttpServletResponse.class);
        method.setAccessible(true);
        for (String databaseStatus : new String[]{"UP", "DOWN"}) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(body));
            when(context.getAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE)).thenReturn(databaseStatus);

            method.invoke(servlet, request, response);

            verify(response).setStatus("UP".equals(databaseStatus)
                    ? HttpServletResponse.SC_OK : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            assertTrue(body.toString().contains("UP".equals(databaseStatus)
                    ? "\"database\":\"UP\"" : "DATABASE_UNAVAILABLE"));
        }
    }

    @Test
    void accountPagesForwardWithVietnameseTitles() throws Exception {
        AccountPageServlet servlet = new AccountPageServlet();
        String[][] pages = {
                {"/auth/login", "Đăng nhập", "/WEB-INF/views/auth/login.jsp"},
                {"/auth/register", "Đăng ký", "/WEB-INF/views/auth/register.jsp"},
                {"/customer/profile", "Hồ sơ", "/WEB-INF/views/customer/profile.jsp"},
                {"/customer/addresses", "Địa chỉ giao hàng", "/WEB-INF/views/customer/addresses.jsp"}
        };
        Method method = AccountPageServlet.class.getDeclaredMethod("doGet",
                HttpServletRequest.class, HttpServletResponse.class);
        method.setAccessible(true);
        for (String[] page : pages) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestDispatcher dispatcher = mock(RequestDispatcher.class);
            when(request.getServletPath()).thenReturn(page[0]);
            when(request.getRequestDispatcher(page[2])).thenReturn(dispatcher);

            method.invoke(servlet, request, response);

            verify(request).setAttribute("pageTitle", page[1]);
            verify(dispatcher).forward(request, response);
        }
    }

    @Test
    void databaseListenerReportsUpAndClosesPoolWithoutLiveDatabase() {
        DatabaseContextListener listener = new DatabaseContextListener();
        ServletContext context = mock(ServletContext.class);
        ServletContextEvent event = new ServletContextEvent(context);
        try (MockedStatic<DatabaseConfig> database = mockStatic(DatabaseConfig.class)) {
            listener.contextInitialized(event);
            verify(context).setAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE, "UP");
            database.verify(DatabaseConfig::verifyConnection);
            listener.contextDestroyed(event);
            database.verify(DatabaseConfig::close);
        }
    }
}
