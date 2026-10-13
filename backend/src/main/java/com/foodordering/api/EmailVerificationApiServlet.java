package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.EmailVerificationService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "emailVerificationApiServlet", urlPatterns = {
        "/api/auth/register/code", "/api/auth/password/reset/code", "/api/auth/password/reset",
        "/api/auth/password/change/code", "/api/auth/password/change"
})
public class EmailVerificationApiServlet extends AccountApiServlet {
    private final EmailVerificationService verificationService;

    public EmailVerificationApiServlet() {
        this(new EmailVerificationService());
    }

    public EmailVerificationApiServlet(EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @Override
    protected String allowedMethods() {
        return "POST";
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            switch (request.getServletPath()) {
                case "/api/auth/register/code" -> {
                    verificationService.requestRegistrationCode(readAccountJson(request, EmailRequest.class).email());
                    codeSent(response, "Mã xác minh đã được gửi đến email của bạn.");
                }
                case "/api/auth/password/reset/code" -> {
                    verificationService.requestPasswordResetCode(readAccountJson(request, EmailRequest.class).email());
                    codeSent(response, "Nếu email thuộc tài khoản đang hoạt động, bạn sẽ nhận được mã xác minh.");
                }
                case "/api/auth/password/reset" -> {
                    PasswordReset reset = readAccountJson(request, PasswordReset.class);
                    requireCode(reset.verificationCode());
                    verificationService.resetPassword(reset.email(), reset.verificationCode(),
                            reset.password(), reset.confirmPassword());
                    completed(response, "Đã đặt lại mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.");
                }
                case "/api/auth/password/change/code" -> {
                    SessionAuth.Snapshot before = SessionAuth.capture(request);
                    CurrentPassword current = readAccountJson(request, CurrentPassword.class);
                    verificationService.requestPasswordChangeCode(before.user().id(), current.currentPassword());
                    codeSent(response, "Mã xác minh đã được gửi đến email của tài khoản.");
                }
                case "/api/auth/password/change" -> {
                    SessionAuth.Snapshot before = SessionAuth.capture(request);
                    PasswordChange change = readAccountJson(request, PasswordChange.class);
                    requireCode(change.verificationCode());
                    verificationService.changePassword(before.user().id(), change.verificationCode(),
                            change.password(), change.confirmPassword());
                    SessionAuth.invalidateIfCurrent(request, before);
                    completed(response, "Đã đổi mật khẩu. Vui lòng đăng nhập lại.");
                }
                default -> methodNotAllowed(response, "POST");
            }
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    private void codeSent(HttpServletResponse response, String message) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(Map.of(
                "message", message, "expiresInSeconds", 600, "resendAfterSeconds", 60)));
    }

    private void completed(HttpServletResponse response, String message) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(Map.of("message", message)));
    }

    static void requireCode(String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new AccountException("VALIDATION_ERROR", "Mã xác minh phải gồm đúng 6 chữ số.", 400,
                    Map.of("verificationCode", "Vui lòng nhập mã gồm 6 chữ số trong email."));
        }
    }

    public record EmailRequest(String email) {}
    public record CurrentPassword(String currentPassword) {}
    public record PasswordReset(String email, String verificationCode, String password, String confirmPassword) {}
    public record PasswordChange(String verificationCode, String password, String confirmPassword) {}
}
