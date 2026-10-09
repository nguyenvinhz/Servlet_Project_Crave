package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.LoginRequest;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.RegisterRequest;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "authApiServlet", urlPatterns = {
        "/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/session"
})
public class AuthApiServlet extends AccountApiServlet {
    private final AccountService accountService;

    public AuthApiServlet() {
        this(new AccountService());
    }

    public AuthApiServlet(AccountService accountService) {
        this.accountService = accountService;
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
                    ProfileResponse user = accountService.login(login);
                    HttpSession session = request.getSession(false);
                    if (session == null) {
                        session = request.getSession(true);
                    } else {
                        request.changeSessionId();
                    }
                    session.setMaxInactiveInterval(Boolean.TRUE.equals(login.rememberMe()) ? 7 * 24 * 60 * 60 : 30 * 60);
                    SessionAuth.store(session, user);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(user));
                }
                case "/api/auth/register" -> writeJson(response, HttpServletResponse.SC_CREATED,
                        ApiResponse.success(accountService.register(readAccountJson(request, RegisterRequest.class))));
                case "/api/auth/logout" -> {
                    HttpSession session = request.getSession(false);
                    if (session != null) {
                        session.invalidate();
                    }
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(Map.of("message", "Đã đăng xuất.")));
                }
                default -> methodNotAllowed(response, "GET");
            }
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"/api/auth/session".equals(request.getServletPath())) {
            methodNotAllowed(response, "POST");
            return;
        }
        try {
            ProfileResponse current = SessionAuth.requireUser(request);
            ProfileResponse fresh = accountService.getProfile(current.id());
            SessionAuth.store(request.getSession(false), fresh);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(fresh));
        } catch (Exception exception) {
            if (exception instanceof com.foodordering.exception.AccountException accountException
                    && accountException.getStatusCode() == 401 && request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            handleError(response, exception);
        }
    }
}
