package com.foodordering.servlet;

import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.EmailVerificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "emailVerificationServlet", urlPatterns = {
        "/auth/register/code", "/auth/password/reset/code", "/auth/password/reset/submit",
        "/auth/password/change/code", "/auth/password/change/submit"
})
public class EmailVerificationServlet extends AccountBaseServlet {
    private final EmailVerificationService verification;
    public EmailVerificationServlet() { this(new EmailVerificationService()); }
    public EmailVerificationServlet(EmailVerificationService verification) { this.verification = verification; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        String page = path.startsWith("/auth/register") ? "auth/register"
                : path.startsWith("/auth/password/change") ? "auth/change-password" : "auth/forgot-password";
        boolean codeRequest = path.endsWith("/code");
        request.setAttribute("otpRequested", !codeRequest || "true".equals(request.getParameter("otpActive")));
        try {
            switch (path) {
                case "/auth/register/code" -> {
                    verification.requestRegistrationCode(request.getParameter("email"));
                    request.setAttribute("message", "Mã xác minh đã được gửi đến email của bạn. Nhập lại mật khẩu và mã để hoàn tất.");
                }
                case "/auth/password/reset/code" -> {
                    verification.requestPasswordResetCode(request.getParameter("email"));
                    request.setAttribute("message", "Nếu email thuộc tài khoản đang hoạt động, bạn sẽ nhận được mã xác minh.");
                }
                case "/auth/password/reset/submit" -> {
                    requireCode(request.getParameter("verificationCode"));
                    verification.resetPassword(request.getParameter("email"), request.getParameter("verificationCode"),
                            request.getParameter("password"), request.getParameter("confirmPassword"));
                    response.sendRedirect(request.getContextPath() + "/auth/login?passwordReset=1");
                    return;
                }
                case "/auth/password/change/code" -> {
                    SessionAuth.Snapshot before = SessionAuth.capture(request);
                    verification.requestPasswordChangeCode(before.user().id(), request.getParameter("currentPassword"));
                    request.setAttribute("message", "Mã xác minh đã được gửi đến email của tài khoản.");
                }
                case "/auth/password/change/submit" -> {
                    SessionAuth.Snapshot before = SessionAuth.capture(request);
                    requireCode(request.getParameter("verificationCode"));
                    verification.changePassword(before.user().id(), request.getParameter("verificationCode"),
                            request.getParameter("password"), request.getParameter("confirmPassword"));
                    SessionAuth.invalidateIfCurrent(request, before);
                    response.sendRedirect(request.getContextPath() + "/auth/login?passwordChanged=1");
                    return;
                }
                default -> {
                    response.sendError(404);
                    return;
                }
            }
            request.setAttribute("otpRequested", true);
            show(request, response, page);
        } catch (Exception exception) {
            formError(request, response, page, exception);
        }
    }

    static void requireCode(String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new AccountException("VALIDATION_ERROR", "Mã xác minh phải gồm đúng 6 chữ số.", 400,
                    Map.of("verificationCode", "Vui lòng nhập mã gồm 6 chữ số trong email."));
        }
    }
}