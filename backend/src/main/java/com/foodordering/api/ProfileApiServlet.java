package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.ProfileUpdateRequest;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "profileApiServlet", urlPatterns = "/api/profile")
public class ProfileApiServlet extends AccountApiServlet {
    private final AccountService accountService;

    public ProfileApiServlet() {
        this(new AccountService());
    }

    public ProfileApiServlet(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected String allowedMethods() {
        return "GET, PUT, POST";
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionAuth.Snapshot snapshot = null;
        try {
            snapshot = SessionAuth.capture(request);
            ProfileResponse user = accountService.getProfile(snapshot.user().id());
            SessionAuth.refresh(request, snapshot, user);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(user));
        } catch (Exception exception) {
            invalidateUnauthorized(request, snapshot, exception);
            handleError(response, exception);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionAuth.Snapshot snapshot = null;
        try {
            snapshot = SessionAuth.capture(request);
            String customerId = SessionAuth.requireCustomer(request).id();
            ProfileResponse updated = accountService.updateProfile(customerId,
                    readAccountJson(request, ProfileUpdateRequest.class));
            SessionAuth.refresh(request, snapshot, updated);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
        } catch (Exception exception) {
            invalidateUnauthorized(request, snapshot, exception);
            handleError(response, exception);
        }
    }

    private static void invalidateUnauthorized(HttpServletRequest request, SessionAuth.Snapshot snapshot,
                                              Exception exception) {
        if (exception instanceof AccountException accountException && accountException.getStatusCode() == 401) {
            SessionAuth.invalidateIfCurrent(request, snapshot);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doPut(request, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        methodNotAllowed(response, allowedMethods());
    }
}
