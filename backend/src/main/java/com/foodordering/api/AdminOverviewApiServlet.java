package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.exception.ValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminOverviewService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@WebServlet(name = "adminOverviewApiServlet", urlPatterns = "/api/admin/overview")
public class AdminOverviewApiServlet extends BaseApiServlet {
    private AdminOverviewService overview;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.ADMIN);
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            LocalDate from = date(request.getParameter("from"), today.minusDays(6));
            LocalDate to = date(request.getParameter("to"), today);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(service().overview(from, to)));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    private synchronized AdminOverviewService service() {
        if (overview == null) overview = new AdminOverviewService();
        return overview;
    }

    private static LocalDate date(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ValidationException("Ngày phải có định dạng YYYY-MM-DD.");
        }
    }
}
