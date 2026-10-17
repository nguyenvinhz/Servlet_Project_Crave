package com.foodordering.servlet;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.ProfileUpdateRequest;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "profileServlet", urlPatterns = "/customer/profile/save")
public class ProfileServlet extends AccountBaseServlet {
    private final AccountService accounts;
    public ProfileServlet() { this(new AccountService()); }
    public ProfileServlet(AccountService accounts) { this.accounts = accounts; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            SessionAuth.Snapshot before = SessionAuth.capture(request);
            ProfileResponse updated = accounts.updateProfile(SessionAuth.requireCustomer(request).id(),
                    new ProfileUpdateRequest(request.getParameter("fullName"), request.getParameter("email"),
                            request.getParameter("phone")));
            SessionAuth.refresh(request, before, updated);
            response.sendRedirect(request.getContextPath() + "/customer/profile?saved=1");
        } catch (Exception exception) {
            request.setAttribute("profile", SessionAuth.captureLogin(request).user());
            formError(request, response, "customer/profile", exception);
        }
    }
}