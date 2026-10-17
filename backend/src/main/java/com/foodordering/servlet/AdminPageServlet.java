package com.foodordering.servlet;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminAccountService;
import com.foodordering.service.AdminOverviewService;
import com.foodordering.service.AdminPromotionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@WebServlet(name = "AdminPageServlet", urlPatterns = {"/admin", "/admin/", "/admin/customers", "/admin/employees", "/admin/promotions"})
public class AdminPageServlet extends BaseServlet {
    private AdminAccountService accounts;
    private AdminPromotionService promotions;
    private AdminOverviewService overview;
    public AdminPageServlet() { }
    public AdminPageServlet(AdminAccountService accounts, AdminPromotionService promotions, AdminOverviewService overview) {
        this.accounts = accounts; this.promotions = promotions; this.overview = overview;
    }
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            var user = SessionAuth.requireEmployee(request, EmployeeRole.values());
            String path = request.getServletPath(), page;
            if ("/admin".equals(path) || "/admin/".equals(path)) {
                String destination = switch (user.role()) {
                    case MENU_MANAGER -> "/admin/menu";
                    case ORDER_STAFF -> "/admin/orders";
                    case HR_MANAGER -> "/admin/customers";
                    case PROMOTION_MANAGER -> "/admin/promotions";
                    case ADMIN -> null;
                };
                if (destination != null) { response.sendRedirect(request.getContextPath() + destination); return; }
                page = "overview";
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
                request.setAttribute("report", overview().overview(today.minusDays(6), today));
            } else if ("/admin/customers".equals(path) || "/admin/employees".equals(path)) {
                SessionAuth.requireEmployee(request, EmployeeRole.HR_MANAGER);
                page = path.endsWith("customers") ? "customers" : "employees";
                request.setAttribute("accounts", "customers".equals(page) ? accounts().listCustomers() : accounts().listEmployees());
            } else if ("/admin/promotions".equals(path)) {
                SessionAuth.requireEmployee(request, EmployeeRole.PROMOTION_MANAGER);
                page = "promotions";
                request.setAttribute("promotions", promotions().listPromotions());
            } else { response.sendError(404); return; }
            request.getRequestDispatcher("/WEB-INF/views/admin/" + page + ".jsp").forward(request, response);
        } catch (Exception exception) { handleError(response, exception); }
    }
    private synchronized AdminAccountService accounts() { if (accounts == null) accounts = new AdminAccountService(); return accounts; }
    private synchronized AdminPromotionService promotions() { if (promotions == null) promotions = new AdminPromotionService(); return promotions; }
    private synchronized AdminOverviewService overview() { if (overview == null) overview = new AdminOverviewService(); return overview; }
}
