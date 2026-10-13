package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.LoginRequest;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.RegisterRequest;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import com.foodordering.service.EmailVerificationService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "authApiServlet", urlPatterns = {
        "/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/session"
})
public class AuthApiServlet extends AccountApiServlet {
    private final AccountService accountService;
    private final EmailVerificationService verificationService;

    public AuthApiServlet() {
        this(new AccountService());
    }

    public AuthApiServlet(AccountService accountService) {
        this(accountService, new EmailVerificationService());
    }

    public AuthApiServlet(AccountService accountService, EmailVerificationService verificationService) {
        this.accountService = accountService;
        this.verificationService = verificationService;
    }

    @Override
    protected String allowedMethods() {
        return "GET, POST";
    }

    @Override
    protected String allowedMethods(HttpServletRequest request) {
        return "/api/auth/session".equals(request.getServletPath()) ? "GET" : "POST";
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            switch (request.getServletPath()) {
                case "/api/auth/login" -> {
                    LoginRequest login = readAccountJson(request, LoginRequest.class);
                    SessionAuth.Snapshot before = SessionAuth.captureLogin(request);
                    ProfileResponse user = accountService.login(login);
                    SessionAuth.login(request, before, user,
                            Boolean.TRUE.equals(login.rememberMe()) ? 7 * 24 * 60 * 60 : 30 * 60);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(user));
                }
                case "/api/auth/register" -> {
                    VerifiedRegistration registration = readAccountJson(request, VerifiedRegistration.class);
                    EmailVerificationApiServlet.requireCode(registration.verificationCode());
                    writeJson(response, HttpServletResponse.SC_CREATED,
                            ApiResponse.success(verificationService.register(registration.registration(),
                                    registration.verificationCode())));
                }
                case "/api/auth/logout" -> {
                    SessionAuth.logout(request);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(Map.of("message", "Đã đăng xuất.")));
                }
                default -> methodNotAllowed(response, "GET");
            }
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    public record VerifiedRegistration(String fullName, String email, String phone,
                                       String password, String confirmPassword, String verificationCode) {
        RegisterRequest registration() {
            return new RegisterRequest(fullName, email, phone, password, confirmPassword);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/api/auth/session".equals(request.getServletPath())) {
            methodNotAllowed(response, "POST");
            return;
        }
        SessionAuth.Snapshot snapshot = null;
        try {
            snapshot = SessionAuth.capture(request);
            ProfileResponse fresh = accountService.getProfile(snapshot.user().id());
            SessionAuth.refresh(request, snapshot, fresh);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(fresh));
        } catch (Exception exception) {
            if (exception instanceof com.foodordering.exception.AccountException accountException
                    && accountException.getStatusCode() == 401) {
                SessionAuth.invalidateIfCurrent(request, snapshot);
            }
            handleError(response, exception);
        }
    }
}
