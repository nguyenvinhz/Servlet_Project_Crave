package vn.edu.crave.controller.admin;

import vn.edu.crave.controller.BaseServlet;

import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.exception.ValidationException;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.AdminOverviewService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@WebServlet(name = "AdminOverviewServlet", urlPatterns = "/admin/reports")
public class AdminOverviewServlet extends BaseServlet {
    private AdminOverviewService overview;
    public AdminOverviewServlet() { }
    public AdminOverviewServlet(AdminOverviewService overview) { this.overview = overview; }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.ADMIN);
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            LocalDate from = date(request.getParameter("from"), today.minusDays(6)), to = date(request.getParameter("to"), today);
            request.setAttribute("report", service().overview(from, to));
            request.getRequestDispatcher("/WEB-INF/views/admin/reports.jsp").forward(request, response);
        } catch (Exception exception) { handleError(response, exception); }
    }
    private synchronized AdminOverviewService service() {
        if (overview == null) overview = new AdminOverviewService();
        return overview;
    }
    private static LocalDate date(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw new ValidationException("Ngày phải có định dạng YYYY-MM-DD."); }
    }
}
