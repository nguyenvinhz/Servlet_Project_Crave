package com.foodordering.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "menuPageServlet", urlPatterns = {
        "/menu",
        "/menu/detail",
        "/admin/menu",
        "/admin/categories"
})
public class MenuPageServlet extends HttpServlet {

    private static final Map<String, Page> PAGES = Map.of(
            "/menu", new Page("Thực đơn", "/WEB-INF/views/menu/index.jsp"),
            "/menu/detail", new Page("Chi tiết món ăn", "/WEB-INF/views/menu/detail.jsp"),
            "/admin/menu", new Page("Quản lý thực đơn", "/WEB-INF/views/admin/menu.jsp"),
            "/admin/categories", new Page("Quản lý danh mục", "/WEB-INF/views/admin/categories.jsp")
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
