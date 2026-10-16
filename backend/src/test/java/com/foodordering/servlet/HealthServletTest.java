package com.foodordering.servlet;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.listener.DatabaseContextListener;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthServletTest {
    @Test void probesDatabaseForEveryRequestAndUsesPlainText() throws Exception {
        var servlet = new HealthServlet();
        var config = mock(ServletConfig.class);
        var context = mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        servlet.init(config);
        for (boolean up : new boolean[]{true, false}) {
            var request = mock(HttpServletRequest.class);
            var response = mock(HttpServletResponse.class);
            var body = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(body));
            try (var database = mockStatic(DatabaseConfig.class)) {
                if (!up) database.when(DatabaseConfig::verifyConnection).thenThrow(new IllegalStateException("offline"));
                servlet.doGet(request, response);
                database.verify(DatabaseConfig::verifyConnection);
            }
            verify(response).setStatus(up ? 200 : 503);
            verify(response).setContentType("text/plain;charset=UTF-8");
            assertEquals("application=UP\ndatabase=" + (up ? "UP" : "DOWN") + "\n", body.toString());
        }
    }
    @Test void listenerStartsAndClosesThePool() {
        var context = mock(ServletContext.class);
        var listener = new DatabaseContextListener();
        var event = new ServletContextEvent(context);
        try (var database = mockStatic(DatabaseConfig.class)) {
            listener.contextInitialized(event);
            verify(context).setAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE, "UP");
            database.verify(DatabaseConfig::verifyConnection);
            listener.contextDestroyed(event);
            database.verify(DatabaseConfig::close);
        }
    }
}
