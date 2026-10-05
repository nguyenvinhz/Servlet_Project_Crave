package com.foodordering.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class DatabaseContextListener implements ServletContextListener {

    public static final String DATABASE_STATUS_ATTRIBUTE = "databaseStatus";
    public static final String DATABASE_ERROR_ATTRIBUTE = "databaseError";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        try {
            DatabaseConfig.verifyConnection();
            context.setAttribute(DATABASE_STATUS_ATTRIBUTE, "UP");
            context.log("Crave database connection pool started successfully.");
        } catch (RuntimeException exception) {
            context.setAttribute(DATABASE_STATUS_ATTRIBUTE, "DOWN");
            context.setAttribute(DATABASE_ERROR_ATTRIBUTE, rootMessage(exception));
            context.log("Database is unavailable. The application remains available for diagnostics.", exception);
            if (Boolean.parseBoolean(System.getenv().getOrDefault("DB_FAIL_FAST", "false"))) {
                throw exception;
            }
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        DatabaseConfig.close();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
