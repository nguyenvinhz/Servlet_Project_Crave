package vn.edu.crave.controller.admin;

import vn.edu.crave.controller.BaseServlet;

import vn.edu.crave.model.form.EmployeeCreateRequest;
import vn.edu.crave.model.enums.CustomerStatus;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.EmployeeStatus;
import vn.edu.crave.exception.AppException;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.AdminAccountService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;

@WebServlet(name = "AdminAccountServlet", urlPatterns = {"/admin/customers/action", "/admin/employees/action"})
public class AdminAccountServlet extends BaseServlet {
    private final AdminAccountService accounts;

    public AdminAccountServlet() { this(new AdminAccountService()); }
    public AdminAccountServlet(AdminAccountService accounts) { this.accounts = accounts; }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String page = request.getServletPath().contains("customers") ? "/admin/customers" : "/admin/employees";
        try {
            String actor = SessionAuth.requireEmployee(request, EmployeeRole.HR_MANAGER).id();
            String action = request.getParameter("action"), id = request.getParameter("id");
            if ("status".equals(action)) {
                if ("/admin/customers".equals(page)) {
                    accounts.updateCustomerStatus(actor, id, CustomerStatus.valueOf(request.getParameter("status")));
                } else {
                    accounts.updateEmployeeStatus(actor, id, EmployeeStatus.valueOf(request.getParameter("status")));
                }
            } else if ("role".equals(action) && "/admin/employees".equals(page)) {
                accounts.updateEmployeeRole(actor, id, EmployeeRole.valueOf(request.getParameter("role")));
            } else if ("create".equals(action) && "/admin/employees".equals(page)) {
                accounts.createEmployee(actor, new EmployeeCreateRequest(request.getParameter("fullName"),
                        request.getParameter("email"), request.getParameter("phone"), request.getParameter("password"),
                        EmployeeRole.valueOf(request.getParameter("role")), request.getParameter("address"),
                        LocalDate.parse(request.getParameter("hireDate"))));
            } else {
                response.sendError(400, "Thao tác tài khoản không hợp lệ."); return;
            }
            request.getSession().setAttribute("adminSuccess", "Đã lưu thông tin tài khoản.");
        } catch (AppException exception) {
            if (exception.getStatusCode() == 401 || exception.getStatusCode() == 403) { handleError(response, exception); return; }
            request.getSession().setAttribute("adminError", exception.getMessage());
        } catch (IllegalArgumentException | NullPointerException | java.time.format.DateTimeParseException exception) {
            request.getSession().setAttribute("adminError", "Vui lòng kiểm tra các trường thông tin tài khoản.");
        } catch (Exception exception) {
            handleError(response, exception); return;
        }
        response.sendRedirect(request.getContextPath() + page);
    }
}
