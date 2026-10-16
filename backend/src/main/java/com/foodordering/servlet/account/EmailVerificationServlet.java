package com.foodordering.servlet.account;

import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.EmailVerificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "EmailVerificationServlet", urlPatterns = {"/auth/forgot-password", "/auth/change-password"})
public class EmailVerificationServlet extends AccountServlet {
    private final EmailVerificationService verificationService;

    public EmailVerificationServlet() { this(new EmailVerificationService()); }

    public EmailVerificationServlet(EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (isChange(request)) {
            try { request.setAttribute("profile", SessionAuth.requireUser(request)); }
            catch (Exception exception) { if (error(request, response, exception)) return; }
        }
        showPage(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        try {
            boolean sendCode = "sendCode".equals(request.getParameter("action"));
            if (isChange(request)) {
                SessionAuth.Snapshot before = SessionAuth.capture(request);
                request.setAttribute("profile", before.user());
                if (sendCode) {
                    verificationService.requestPasswordChangeCode(before.user().id(), request.getParameter("currentPassword"));
                } else {
                    requireCode(request.getParameter("verificationCode"));
                    verificationService.changePassword(before.user().id(), request.getParameter("verificationCode"),
                            request.getParameter("password"), request.getParameter("confirmPassword"));
                    SessionAuth.invalidateIfCurrent(request, before);
                    redirect(request, response, "/auth/login", "Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
                    return;
                }
            } else if (sendCode) {
                verificationService.requestPasswordResetCode(request.getParameter("email"));
            } else {
                requireCode(request.getParameter("verificationCode"));
                verificationService.resetPassword(request.getParameter("email"), request.getParameter("verificationCode"),
                        request.getParameter("password"), request.getParameter("confirmPassword"));
                loginPage(request, response, "Đã đặt lại mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.");
                return;
            }
            request.setAttribute("message", isChange(request)
                    ? "Mã xác minh đã được gửi đến email của tài khoản. Mã có hiệu lực trong 10 phút."
                    : "Nếu email thuộc tài khoản đang hoạt động, bạn sẽ nhận được mã xác minh. Mã có hiệu lực trong 10 phút.");
        } catch (Exception exception) { if (error(request, response, exception)) return; }
        showPage(request, response);
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        page(request, response, isChange(request) ? "Đổi mật khẩu" : "Quên mật khẩu",
                isChange(request) ? "auth/change-password" : "auth/forgot-password");
    }

    private static boolean isChange(HttpServletRequest request) {
        return "/auth/change-password".equals(request.getServletPath());
    }

    static void requireCode(String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new AccountException("VALIDATION_ERROR", "Mã xác minh phải gồm đúng 6 chữ số.", 400,
                    Map.of("verificationCode", "Vui lòng nhập mã gồm 6 chữ số trong email."));
        }
    }
}
