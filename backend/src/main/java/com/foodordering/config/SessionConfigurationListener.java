package com.foodordering.config;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class SessionConfigurationListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        SessionCookieConfig cookie = event.getServletContext().getSessionCookieConfig();
        cookie.setHttpOnly(true);
        cookie.setAttribute("SameSite", "Lax");
    }
}
