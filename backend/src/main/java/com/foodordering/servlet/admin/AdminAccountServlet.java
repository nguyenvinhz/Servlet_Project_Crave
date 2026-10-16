package com.foodordering.servlet.admin;

import com.foodordering.bean.EmployeeCreateForm;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.exception.ValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@WebServlet(name = "AdminAccountServlet", urlPatterns = {"/admin/customers", "/admin/employees"})
public class AdminAccountServlet extends HttpServlet {
    private AdminAccountService accounts;
    public AdminAccountServlet() { }
    public AdminAccountServlet(AdminAccountService accounts) { this.accounts = accounts; }
    @Override public void init() { if (accounts == null) accounts = new AdminAccountService(); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        var actor = SessionAuth.requireEmployee(request, EmployeeRole.HR_MANAGER);
        AdminForms.flash(request);
        String keyword = AdminForms.text(request, "keyword").toLowerCase(Locale.ROOT);
        String status = AdminForms.text(request, "status");
        boolean employees = "/admin/employees".equals(request.getServletPath());
        if (employees) {
            var all = accounts.listEmployees();
            String role = AdminForms.text(request, "role");
            request.setAttribute("employees", all.stream().filter(e -> (status.isEmpty() || e.status().name().equals(status))
                    && (role.isEmpty() || e.role().name().equals(role)) && matches(keyword, e.id(), e.fullName(), e.email(), e.phone())).toList());
            request.setAttribute("totalCount", all.size());
            request.setAttribute("activeCount", all.stream().filter(e -> e.status() == EmployeeStatus.WORKING).count());
            request.setAttribute("inactiveCount", all.stream().filter(e -> e.status() != EmployeeStatus.WORKING).count());
            request.setAttribute("roles", java.util.Arrays.stream(EmployeeRole.values()).filter(r -> r != EmployeeRole.ADMIN || actor.role() == EmployeeRole.ADMIN).toList());
            request.setAttribute("today", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        } else {
            var all = accounts.listCustomers();
            request.setAttribute("customers", all.stream().filter(c -> (status.isEmpty() || c.status().name().equals(status))
                    && matches(keyword, c.id(), c.fullName(), c.email(), c.phone())).toList());
            request.setAttribute("totalCount", all.size());
            request.setAttribute("activeCount", all.stream().filter(c -> c.status() == CustomerStatus.ACTIVE).count());
            request.setAttribute("inactiveCount", all.stream().filter(c -> c.status() != CustomerStatus.ACTIVE).count());
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/" + (employees ? "employees" : "customers") + ".jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String actorId = SessionAuth.requireEmployee(request, EmployeeRole.HR_MANAGER).id();
        Exception error = null;
        try {
            String action = AdminForms.text(request, "action"), id = AdminForms.text(request, "id");
            if ("/admin/customers".equals(request.getServletPath())) {
                if (!"status".equals(action)) throw new ValidationException("Thao tác khách hàng không hợp lệ.");
                accounts.updateCustomerStatus(actorId, id, AdminForms.choice(request, "status", CustomerStatus.class));
            } else switch (action) {
                case "create" -> {
                    LocalDate hireDate;
                    try { hireDate = LocalDate.parse(AdminForms.text(request, "hireDate")); }
                    catch (DateTimeParseException exception) { throw new ValidationException("Ngày vào làm không hợp lệ."); }
                    accounts.createEmployee(actorId, new EmployeeCreateForm(request.getParameter("fullName"), request.getParameter("email"),
                            request.getParameter("phone"), request.getParameter("password"), AdminForms.choice(request, "role", EmployeeRole.class),
                            request.getParameter("address"), hireDate));
                }
                case "status" -> accounts.updateEmployeeStatus(actorId, id, AdminForms.choice(request, "status", EmployeeStatus.class));
                case "role" -> accounts.updateEmployeeRole(actorId, id, AdminForms.choice(request, "role", EmployeeRole.class));
                default -> throw new ValidationException("Thao tác nhân viên không hợp lệ.");
            }
        } catch (Exception exception) { error = exception; }
        AdminForms.result(request, response, request.getServletPath(), error);
    }

    private static boolean matches(String keyword, String... values) {
        if (keyword.isEmpty()) return true;
        for (String value : values) if (value != null && value.toLowerCase(Locale.ROOT).contains(keyword)) return true;
        return false;
    }
}
