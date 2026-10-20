package vn.edu.crave.controller.account;

import vn.edu.crave.model.form.LoginRequest;
import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.AccountService;
import vn.edu.crave.model.service.EmailVerificationService;
import vn.edu.crave.model.service.EmailVerificationService.PendingRegistration;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "authServlet", urlPatterns = {
        "/auth/login/submit", "/auth/register/submit", "/auth/logout"
})
public class AuthServlet extends AccountBaseServlet {
    private final AccountService accounts;
    private final EmailVerificationService verification;

    public AuthServlet() { this(new AccountService(), new EmailVerificationService()); }
    public AuthServlet(AccountService accounts) { this(accounts, new EmailVerificationService()); }
    public AuthServlet(AccountService accounts, EmailVerificationService verification) {
        this.accounts = accounts;
        this.verification = verification;
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if ("/auth/logout".equals(path)) {
            SessionAuth.logout(request);
            response.sendRedirect(request.getContextPath() + "/auth/login?loggedOut=1");
            return;
        }
        try {
            if ("/auth/register/submit".equals(path)) {
                PendingRegistration pending = RegistrationSession.require(request);
                String code = request.getParameter("verificationCode");
                EmailVerificationServlet.requireCode(code);
                verification.register(pending, code);
                RegistrationSession.clearIfCurrent(request, pending);
                response.sendRedirect(request.getContextPath() + "/auth/login?registered=1");
                return;
            }
            SessionAuth.Snapshot before = SessionAuth.captureLogin(request);
            ProfileResponse user = accounts.login(new LoginRequest(request.getParameter("email"),
                    request.getParameter("password"), "true".equals(request.getParameter("rememberMe"))));
            SessionAuth.login(request, before, user,
                    "true".equals(request.getParameter("rememberMe")) ? 7 * 24 * 60 * 60 : 30 * 60);
            request.getSession(false).removeAttribute("selectedPromotionId");
            request.getSession(false).removeAttribute("selectedPromotionCode");
            request.getSession(false).removeAttribute("cartItemCount");
            String destination = safeReturnTo(request.getParameter("returnTo"), request.getContextPath(), user);
            response.sendRedirect(destination == null ? request.getContextPath() + destination(user) : destination);
        } catch (Exception exception) {
            boolean registration = "/auth/register/submit".equals(path);
            if (!registration && exception instanceof vn.edu.crave.exception.AccountException account
                    && account.getStatusCode() == 401) {
                response.setStatus(401);
                request.setAttribute("error", exception.getMessage());
                show(request, response, "auth/login");
            } else formError(request, response, registration ? "auth/register" : "auth/login", exception);
        }
    }

    static String destination(ProfileResponse user) {
        if (user.accountType() != AccountType.EMPLOYEE || user.role() == null) return "/menu";
        return switch (user.role()) {
            case ADMIN -> "/admin";
            case MENU_MANAGER -> "/admin/menu";
            case ORDER_STAFF -> "/admin/orders";
            case PROMOTION_MANAGER -> "/admin/promotions";
            case HR_MANAGER -> "/admin/employees";
        };
    }

    static String safeReturnTo(String value, String context, ProfileResponse user) {
        if (value != null && value.startsWith("/") && !value.startsWith("//")
                && !context.isEmpty() && !value.startsWith(context + "/")) value = context + value;
        if (value == null || !value.startsWith(context + "/") || value.startsWith("//")
                || value.contains("\\") || value.contains(";") || value.chars().anyMatch(c -> c < 32 || c == 127)
                || value.contains("%") || value.contains("..")) return null;
        String route = value.substring(context.length()).split("[?#]", 2)[0];
        if (route.startsWith("/auth") || route.startsWith("/api")) return null;
        if (user.accountType() != AccountType.EMPLOYEE) return route.startsWith("/admin") ? null : value;
        if (route.startsWith("/cart") || route.startsWith("/checkout") || route.startsWith("/orders")
                || route.startsWith("/customer/addresses")) return null;
        if (route.startsWith("/admin") && !"/admin".equals(destination(user))
                && !route.equals(destination(user))
                && !(user.role() == vn.edu.crave.model.enums.EmployeeRole.MENU_MANAGER && route.equals("/admin/categories"))
                && !(user.role() == vn.edu.crave.model.enums.EmployeeRole.HR_MANAGER && route.equals("/admin/customers"))) return null;
        return value;
    }
}
