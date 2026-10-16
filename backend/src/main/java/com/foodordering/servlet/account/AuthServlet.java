package com.foodordering.servlet.account;

import com.foodordering.bean.LoginForm;
import com.foodordering.bean.ProfileBean;
import com.foodordering.bean.RegisterForm;
import com.foodordering.enums.AccountType;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import com.foodordering.service.EmailVerificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;

@WebServlet(name = "AuthServlet", urlPatterns = {"/auth/login", "/auth/register", "/auth/logout"})
public class AuthServlet extends AccountServlet {
    private final AccountService accountService;
    private final EmailVerificationService verificationService;

    public AuthServlet() { this(new AccountService(), new EmailVerificationService()); }

    public AuthServlet(AccountService accountService, EmailVerificationService verificationService) {
        this.accountService = accountService;
        this.verificationService = verificationService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        switch (request.getServletPath()) {
            case "/auth/login" -> {
                if ("session-expired".equals(request.getParameter("reason"))) {
                    request.setAttribute("error", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
                }
                page(request, response, "Đăng nhập", "auth/login");
            }
            case "/auth/register" -> page(request, response, "Đăng ký", "auth/register");
            default -> response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        String path = request.getServletPath();
        if ("/auth/logout".equals(path)) {
            SessionAuth.logout(request);
            redirect(request, response, "/auth/login", "Đã đăng xuất.");
            return;
        }
        try {
            if ("/auth/login".equals(path)) {
                SessionAuth.Snapshot before = SessionAuth.captureLogin(request);
                ProfileBean user = accountService.login(new LoginForm(request.getParameter("email"),
                        request.getParameter("password"), "true".equals(request.getParameter("rememberMe"))));
                SessionAuth.login(request, before, user,
                        "true".equals(request.getParameter("rememberMe")) ? 7 * 24 * 60 * 60 : 30 * 60);
                String returnTo = returnTo(request, user);
                response.sendRedirect(returnTo == null ? request.getContextPath() + destination(user) : returnTo);
                return;
            }
            if ("sendCode".equals(request.getParameter("action"))) {
                verificationService.requestRegistrationCode(request.getParameter("email"));
                request.setAttribute("message", "Mã xác minh đã được gửi đến email của bạn. Nhập mã bên dưới để đăng ký.");
            } else {
                EmailVerificationServlet.requireCode(request.getParameter("verificationCode"));
                verificationService.register(new RegisterForm(request.getParameter("fullName"),
                        request.getParameter("email"), request.getParameter("phone"), request.getParameter("password"),
                        request.getParameter("confirmPassword")), request.getParameter("verificationCode"));
                loginPage(request, response, "Đăng ký thành công. Vui lòng đăng nhập.");
                return;
            }
        } catch (Exception exception) {
            // Invalid credentials are a form error; the login page must retain the entered email.
            if ("/auth/login".equals(path) && exception instanceof com.foodordering.exception.AppException application
                    && application.getStatusCode() == 401) {
                request.setAttribute("error", application.getMessage());
            } else if (error(request, response, exception)) return;
        }
        page(request, response, "/auth/login".equals(path) ? "Đăng nhập" : "Đăng ký",
                "/auth/login".equals(path) ? "auth/login" : "auth/register");
    }

    private static String destination(ProfileBean user) {
        if (user.accountType() != AccountType.EMPLOYEE || user.role() == null) return "/menu";
        return switch (user.role()) {
            case ADMIN -> "/admin";
            case MENU_MANAGER -> "/admin/menu";
            case ORDER_STAFF -> "/admin/orders";
            case HR_MANAGER -> "/admin/employees";
            case PROMOTION_MANAGER -> "/admin/promotions";
        };
    }

    private static String returnTo(HttpServletRequest request, ProfileBean user) {
        String value = request.getParameter("returnTo");
        if (value == null || !value.startsWith("/") || value.startsWith("//")
                || value.matches(".*[\\\\\\x00-\\x1f\\x7f].*") || value.toLowerCase().matches(".*%(2f|5c|0[ad]).*")) return null;
        try {
            URI uri = URI.create(value);
            String context = request.getContextPath();
            String path = uri.getPath();
            if (uri.isAbsolute() || uri.getRawAuthority() != null || path == null
                    || !path.startsWith(context + "/") || !uri.normalize().getPath().equals(path)) return null;
            String route = path.substring(context.length());
            if (route.matches("^/(auth|api)([;/].*)?$") || route.startsWith("//")) return null;
            if (user.accountType() == AccountType.EMPLOYEE
                    && route.matches("^/(cart|checkout|orders|customer/addresses)([;/].*)?$")) return null;
            if (route.matches("^/admin([;/].*)?$")) {
                if (user.accountType() != AccountType.EMPLOYEE || user.role() == null) return null;
                if (!"ADMIN".equals(user.role().name()) && !route.equals(destination(user))
                        && !route.startsWith(destination(user) + "/")) return null;
            }
            return value;
        } catch (IllegalArgumentException exception) { return null; }
    }
}
