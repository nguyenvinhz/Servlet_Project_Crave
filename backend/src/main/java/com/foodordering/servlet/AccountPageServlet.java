package com.foodordering.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "accountPageServlet", urlPatterns = {
        "/auth/login",
        "/auth/register",
        "/customer/profile",
        "/customer/addresses"
})
public class AccountPageServlet extends HttpServlet {

    private static final Map<String, Page> PAGES = Map.of(
            "/auth/login", new Page("Đăng nhập", "/WEB-INF/views/auth/login.jsp"),
            "/auth/register", new Page("Đăng ký", "/WEB-INF/views/auth/register.jsp"),
            "/customer/profile", new Page("Hồ sơ", "/WEB-INF/views/customer/profile.jsp"),
            "/customer/addresses", new Page("Địa chỉ giao hàng", "/WEB-INF/views/customer/addresses.jsp")
    );

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Page page = PAGES.get(request.getServletPath());
        if (page == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("pageTitle", page.title());
        request.getRequestDispatcher(page.jsp()).forward(request, response);
    }

    private record Page(String title, String jsp) {
    }
}
