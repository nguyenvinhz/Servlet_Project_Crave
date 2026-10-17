package vn.edu.crave.controller;

import vn.edu.crave.config.DatabaseConfig;
import vn.edu.crave.listener.DatabaseContextListener;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "HealthServlet", urlPatterns = {"/health", "/api/health"})
public class HealthServlet extends HttpServlet {
    @Override public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean databaseUp;
        try { DatabaseConfig.verifyConnection(); databaseUp = true; }
        catch (RuntimeException unavailable) { databaseUp = false; }
        getServletContext().setAttribute(DatabaseContextListener.DATABASE_STATUS_ATTRIBUTE, databaseUp ? "UP" : "DOWN");
        response.setStatus(databaseUp ? 200 : 503);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain");
        response.getWriter().print("application=UP\ndatabase=" + (databaseUp ? "UP" : "DOWN") + "\n");
    }
}