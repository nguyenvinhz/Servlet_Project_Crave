package com.foodordering.api;

import com.foodordering.dto.AccountStatusUpdateRequest;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.EmployeeCreateRequest;
import com.foodordering.dto.EmployeeRoleUpdateRequest;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.exception.ValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@WebServlet(name = "AdminAccountApiServlet", urlPatterns = {
        "/api/admin/customers", "/api/admin/customers/*",
        "/api/admin/employees", "/api/admin/employees/*"
})
public class AdminAccountApiServlet extends BaseApiServlet {
    private final AdminAccountService accounts;

    public AdminAccountApiServlet() {
        this(new AdminAccountService());
    }

    public AdminAccountApiServlet(AdminAccountService accounts) {
        this.accounts = Objects.requireNonNull(accounts);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(request.getMethod())) doPatch(request, response);
        else super.service(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            requireManager(request);
            if (!root(request)) {
                badRequest(response, "Đường dẫn danh sách tài khoản không hợp lệ.");
                return;
            }
            Object result = customerRoute(request) ? accounts.listCustomers() : accounts.listEmployees();
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(result));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String actorId = requireManager(request);
            if (customerRoute(request) || !root(request)) {
                badRequest(response, "Chỉ hỗ trợ tạo nhân viên tại /api/admin/employees.");
                return;
            }
            EmployeeCreateRequest body = readJson(request, EmployeeCreateRequest.class);
            writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(accounts.createEmployee(actorId, body)));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String actorId = requireManager(request);
            String path = request.getPathInfo();
            if (path == null || !path.matches("/[^/]+/(status|role)")) {
                badRequest(response, "Đường dẫn phải có dạng /{id}/status hoặc /{id}/role.");
                return;
            }
            String[] segments = path.substring(1).split("/");
            String id = segments[0];
            Object result;
            if ("status".equals(segments[1])) {
                AccountStatusUpdateRequest body = readJson(request, AccountStatusUpdateRequest.class);
                if (body == null || body.status() == null || body.status().isBlank()) {
                    throw new ValidationException("Vui lòng chọn trạng thái tài khoản.");
                }
                String status = body.status().strip().toUpperCase(Locale.ROOT);
                result = customerRoute(request)
                        ? accounts.updateCustomerStatus(actorId, id, CustomerStatus.valueOf(status))
                        : accounts.updateEmployeeStatus(actorId, id, EmployeeStatus.valueOf(status));
            } else {
                if (customerRoute(request)) {
                    badRequest(response, "Khách hàng không có chức vụ nhân viên.");
                    return;
                }
                EmployeeRoleUpdateRequest body = readJson(request, EmployeeRoleUpdateRequest.class);
                if (body == null || body.role() == null) throw new ValidationException("Vui lòng chọn chức vụ.");
                result = accounts.updateEmployeeRole(actorId, id, body.role());
            }
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(result));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    private static String requireManager(HttpServletRequest request) {
        return SessionAuth.requireEmployee(request, EmployeeRole.HR_MANAGER).id();
    }

    private static boolean customerRoute(HttpServletRequest request) {
        return "/api/admin/customers".equals(request.getServletPath());
    }

    private static boolean root(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null || path.isEmpty() || "/".equals(path);
    }
}
