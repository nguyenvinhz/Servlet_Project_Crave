package com.foodordering.servlet.page;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.security.SessionAuth;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** Admin modules share the server-rendered shell and load current data through authorized APIs. */
@WebServlet(name = "AdminPageServlet", urlPatterns = {"/admin", "/admin/", "/admin/customers", "/admin/employees", "/admin/promotions", "/admin/reports"})
public class AdminPageServlet extends HttpServlet {
    private static final Map<String, String> PAGES = Map.of(
            "/admin/customers", "customers", "/admin/employees", "employees",
            "/admin/promotions", "promotions", "/admin/reports", "reports");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        var user = SessionAuth.requireEmployee(request, EmployeeRole.values());
        String path = request.getServletPath();
        if ("/admin".equals(path) || "/admin/".equals(path)) {
            String destination = switch (user.role()) {
                case MENU_MANAGER -> "/admin/menu";
                case ORDER_STAFF -> "/admin/orders";
                case HR_MANAGER -> "/admin/customers";
                case PROMOTION_MANAGER -> "/admin/promotions";
                case ADMIN -> null;
            };
            if (destination != null) { response.sendRedirect(request.getContextPath() + destination); return; }
            request.getRequestDispatcher("/WEB-INF/views/admin/overview.jsp").forward(request, response);
            return;
        }
        String page = PAGES.get(path);
        if (page == null) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
        request.getRequestDispatcher("/WEB-INF/views/admin/" + page + ".jsp").forward(request, response);
    }
}
