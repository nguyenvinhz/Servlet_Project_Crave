package com.foodordering.servlet;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.config.DatabaseContextListener;
import com.foodordering.service.AccountService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FormServletContractTest {
    @Test
    void publicAccountPagesForwardWithVietnameseTitles() throws Exception {
        AccountPageServlet servlet = new AccountPageServlet(mock(AccountService.class));
        String[][] pages = {{"/auth/login", "Đăng nhập", "auth/login"},
                {"/auth/register", "Đăng ký", "auth/register"},
                {"/auth/forgot-password", "Quên mật khẩu", "auth/forgot-password"}};
        for (String[] page : pages) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            RequestDispatcher dispatcher = mock(RequestDispatcher.class);
            when(request.getServletPath()).thenReturn(page[0]);
            when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
            servlet.doGet(request, response);
            verify(request).setAttribute("pageTitle", page[1]);
            verify(request).getRequestDispatcher("/WEB-INF/views/" + page[2] + ".jsp");
            verify(dispatcher).forward(request, response);
        }
    }

    @Test
    void healthProbesDatabaseInsteadOfTrustingStartupState() throws Exception {
        HealthServlet servlet = new HealthServlet();
        ServletConfig config = mock(ServletConfig.class);
        ServletContext context = mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        servlet.init(config);
        for (boolean up : new boolean[]{true, false}) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            when(context.getAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE)).thenReturn(up ? "DOWN" : "UP");
            try (MockedStatic<DatabaseConfig> database = mockStatic(DatabaseConfig.class)) {
                if (!up) database.when(DatabaseConfig::verifyConnection).thenThrow(new IllegalStateException("Database unavailable"));
                servlet.doGet(request, response);
                database.verify(DatabaseConfig::verifyConnection);
            }
            verify(response).setStatus(up ? 200 : 503);
        }
        verify(context).setAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE, "UP");
        verify(context).setAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE, "DOWN");
    }

    @Test
    void databaseListenerVerifiesAndClosesPool() {
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
