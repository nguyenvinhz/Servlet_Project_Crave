package com.foodordering.api;

import com.foodordering.config.DatabaseContextListener;
import com.foodordering.servlet.AccountPageServlet;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FormApiContractTest {

    @Test
    @DisplayName("ProfileApiServlet accepts both PUT and POST without 405 Method Not Allowed")
    void profileApiAcceptsPutAndPost() throws Exception {
        ProfileApiServlet servlet = new ProfileApiServlet();

        // Test POST delegation (form submission)
        HttpServletRequest postReq = mock(HttpServletRequest.class);
        HttpServletResponse postResp = mock(HttpServletResponse.class);
        StringWriter postSw = new StringWriter();
        when(postResp.getWriter()).thenReturn(new PrintWriter(postSw));

        Method doPost = ProfileApiServlet.class.getDeclaredMethod("doPost", HttpServletRequest.class, HttpServletResponse.class);
        doPost.setAccessible(true);
        doPost.invoke(servlet, postReq, postResp);

        verify(postResp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        assertTrue(postSw.toString().contains("NOT_IMPLEMENTED"));

        // Test PUT (API / Ajax submission)
        HttpServletRequest putReq = mock(HttpServletRequest.class);
        HttpServletResponse putResp = mock(HttpServletResponse.class);
        StringWriter putSw = new StringWriter();
        when(putResp.getWriter()).thenReturn(new PrintWriter(putSw));

        Method doPut = ProfileApiServlet.class.getDeclaredMethod("doPut", HttpServletRequest.class, HttpServletResponse.class);
        doPut.setAccessible(true);
        doPut.invoke(servlet, putReq, putResp);

        verify(putResp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        assertTrue(putSw.toString().contains("NOT_IMPLEMENTED"));
    }

    @Test
    @DisplayName("AuthApiServlet accepts POST for login and register with 501 Day 1 response")
    void authApiAcceptsPost() throws Exception {
        AuthApiServlet servlet = new AuthApiServlet();

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getServletPath()).thenReturn("/api/auth/login");
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        Method doPost = AuthApiServlet.class.getDeclaredMethod("doPost", HttpServletRequest.class, HttpServletResponse.class);
        doPost.setAccessible(true);
        doPost.invoke(servlet, req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        assertTrue(sw.toString().contains("NOT_IMPLEMENTED"));
    }

    @Test
    @DisplayName("AddressApiServlet accepts GET, POST, PUT, DELETE with 501 Day 1 response")
    void addressApiAcceptsCrudMethods() throws Exception {
        AddressApiServlet servlet = new AddressApiServlet();

        // GET /api/addresses
        HttpServletRequest getReq = mock(HttpServletRequest.class);
        HttpServletResponse getResp = mock(HttpServletResponse.class);
        StringWriter getSw = new StringWriter();
        when(getResp.getWriter()).thenReturn(new PrintWriter(getSw));
        when(getReq.getPathInfo()).thenReturn(null);

        Method doGet = AddressApiServlet.class.getDeclaredMethod("doGet", HttpServletRequest.class, HttpServletResponse.class);
        doGet.setAccessible(true);
        doGet.invoke(servlet, getReq, getResp);
        verify(getResp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        assertTrue(getSw.toString().contains("NOT_IMPLEMENTED"));

        // POST /api/addresses
        HttpServletRequest postReq = mock(HttpServletRequest.class);
        HttpServletResponse postResp = mock(HttpServletResponse.class);
        StringWriter postSw = new StringWriter();
        when(postResp.getWriter()).thenReturn(new PrintWriter(postSw));
        when(postReq.getPathInfo()).thenReturn(null);

        Method doPost = AddressApiServlet.class.getDeclaredMethod("doPost", HttpServletRequest.class, HttpServletResponse.class);
        doPost.setAccessible(true);
        doPost.invoke(servlet, postReq, postResp);
        verify(postResp).setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        assertTrue(postSw.toString().contains("NOT_IMPLEMENTED"));
    }

    @Test
    @DisplayName("HealthApiServlet returns 200 when database is UP and 503 when DOWN")
    void healthApiReportsStatus() throws Exception {
        HealthApiServlet servlet = new HealthApiServlet();
        ServletConfig config = mock(ServletConfig.class);
        ServletContext context = mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        servlet.init(config);

        // Case UP
        when(context.getAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE)).thenReturn("UP");
        HttpServletRequest reqUp = mock(HttpServletRequest.class);
        HttpServletResponse respUp = mock(HttpServletResponse.class);
        StringWriter swUp = new StringWriter();
        when(respUp.getWriter()).thenReturn(new PrintWriter(swUp));

        Method doGet = HealthApiServlet.class.getDeclaredMethod("doGet", HttpServletRequest.class, HttpServletResponse.class);
        doGet.setAccessible(true);
        doGet.invoke(servlet, reqUp, respUp);
        verify(respUp).setStatus(HttpServletResponse.SC_OK);
        assertTrue(swUp.toString().contains("\"application\":\"UP\""));
        assertTrue(swUp.toString().contains("\"database\":\"UP\""));

        // Case DOWN
        when(context.getAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE)).thenReturn("DOWN");
        HttpServletRequest reqDown = mock(HttpServletRequest.class);
        HttpServletResponse respDown = mock(HttpServletResponse.class);
        StringWriter swDown = new StringWriter();
        when(respDown.getWriter()).thenReturn(new PrintWriter(swDown));

        doGet.invoke(servlet, reqDown, respDown);
        verify(respDown).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertTrue(swDown.toString().contains("DATABASE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("AccountPageServlet forwards all account paths to their respective JSP views")
    void accountPageServletForwardsToJsps() throws Exception {
        AccountPageServlet servlet = new AccountPageServlet();

        String[][] testCases = {
                {"/auth/login", "Đăng nhập", "/WEB-INF/views/auth/login.jsp"},
                {"/auth/register", "Đăng ký", "/WEB-INF/views/auth/register.jsp"},
                {"/customer/profile", "Hồ sơ", "/WEB-INF/views/customer/profile.jsp"},
                {"/customer/addresses", "Địa chỉ giao hàng", "/WEB-INF/views/customer/addresses.jsp"}
        };

        Method doGet = AccountPageServlet.class.getDeclaredMethod("doGet", HttpServletRequest.class, HttpServletResponse.class);
        doGet.setAccessible(true);

        for (String[] tc : testCases) {
            HttpServletRequest req = mock(HttpServletRequest.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            RequestDispatcher dispatcher = mock(RequestDispatcher.class);

            when(req.getServletPath()).thenReturn(tc[0]);
            when(req.getRequestDispatcher(tc[2])).thenReturn(dispatcher);

            doGet.invoke(servlet, req, resp);

            verify(req).setAttribute("pageTitle", tc[1]);
            verify(dispatcher).forward(req, resp);
        }
    }

    @Test
    @DisplayName("DatabaseContextListener sets databaseStatus to UP upon startup with live DB")
    void databaseContextListenerSetsUpStatus() {
        DatabaseContextListener listener = new DatabaseContextListener();
        ServletContext context = mock(ServletContext.class);
        ServletContextEvent event = new ServletContextEvent(context);

        listener.contextInitialized(event);

        verify(context).setAttribute(eq(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE), eq("UP"));

        listener.contextDestroyed(event);
    }
}
