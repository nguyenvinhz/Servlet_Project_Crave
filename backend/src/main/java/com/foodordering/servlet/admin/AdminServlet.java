package com.foodordering.servlet.admin;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.AppException;
import com.foodordering.exception.ValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminOverviewService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@WebServlet(name = "AdminServlet", urlPatterns = {"/admin", "/admin/", "/admin/reports"})
public class AdminServlet extends HttpServlet {
    private AdminOverviewService overview;
    public AdminServlet() { }
    public AdminServlet(AdminOverviewService overview) { this.overview = overview; }
    @Override public void init() { if (overview == null) overview = new AdminOverviewService(); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        var user = SessionAuth.requireEmployee(request, EmployeeRole.values());
        if (user.role() != EmployeeRole.ADMIN) {
            String destination = switch (user.role()) {
                case MENU_MANAGER -> "/admin/menu";
                case ORDER_STAFF -> "/admin/orders";
                case HR_MANAGER -> "/admin/customers";
                case PROMOTION_MANAGER -> "/admin/promotions";
                default -> "/admin";
            };
            response.sendRedirect(request.getContextPath() + destination); return;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        try {
            String days = request.getParameter("days");
            int range = "30".equals(days) ? 30 : "90".equals(days) ? 90 : 7;
            LocalDate from = date(request.getParameter("from"), today.minusDays(range - 1));
            LocalDate to = date(request.getParameter("to"), today);
            Map<String, Object> report = overview.overview(from, to);
            if ("csv".equals(request.getParameter("download"))) { csv(response, report); return; }
            report.forEach(request::setAttribute);
            request.setAttribute("from", from);
            request.setAttribute("to", to);
            @SuppressWarnings("unchecked") List<Map<String, Object>> daily = (List<Map<String, Object>>) report.get("daily");
            request.setAttribute("maximumRevenue", daily.stream().map(row -> new java.math.BigDecimal(row.get("revenue").toString()))
                    .max(java.math.BigDecimal::compareTo).orElse(java.math.BigDecimal.ZERO));
        } catch (AppException exception) { response.sendError(exception.getStatusCode(), exception.getMessage()); return; }
        request.getRequestDispatcher("/WEB-INF/views/admin/" + ("/admin/reports".equals(request.getServletPath()) ? "reports" : "overview") + ".jsp").forward(request, response);
    }

    private static LocalDate date(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw new ValidationException("Ngày phải có định dạng YYYY-MM-DD."); }
    }

    private static void csv(HttpServletResponse response, Map<String, Object> report) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=crave-report.csv");
        var writer = response.getWriter();
        writer.write('\uFEFF');
        writer.println("Ngày,Số đơn hàng,Doanh thu đã thanh toán");
        @SuppressWarnings("unchecked") var rows = (List<Map<String, Object>>) report.get("daily");
        for (var row : rows) writer.println(row.get("date") + "," + row.get("orderCount") + "," + row.get("revenue"));
    }
}
