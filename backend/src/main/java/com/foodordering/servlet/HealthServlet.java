package com.foodordering.servlet;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.listener.DatabaseContextListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(name = "HealthServlet", urlPatterns = {"/health", "/api/health"})
public class HealthServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean up;
        try { DatabaseConfig.verifyConnection(); up = true; } catch (RuntimeException unavailable) { up = false; }
        getServletContext().setAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE, up ? "UP" : "DOWN");
        response.setStatus(up ? 200 : 503);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().print("application=UP\ndatabase=" + (up ? "UP" : "DOWN") + "\n");
    }
}
