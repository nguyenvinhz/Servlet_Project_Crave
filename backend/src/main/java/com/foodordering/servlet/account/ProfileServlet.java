package com.foodordering.servlet.account;

import com.foodordering.bean.ProfileBean;
import com.foodordering.bean.ProfileUpdateForm;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "ProfileServlet", urlPatterns = "/customer/profile")
public class ProfileServlet extends AccountServlet {
    private final AccountService accountService;

    public ProfileServlet() { this(new AccountService()); }
    public ProfileServlet(AccountService accountService) { this.accountService = accountService; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            SessionAuth.Snapshot before = SessionAuth.capture(request);
            ProfileBean profile = accountService.getProfile(before.user().id());
            SessionAuth.refresh(request, before, profile);
            request.setAttribute("profile", profile);
        } catch (Exception exception) { if (error(request, response, exception)) return; }
        page(request, response, "Hồ sơ", "customer/profile");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        try {
            SessionAuth.Snapshot before = SessionAuth.capture(request);
            String customerId = SessionAuth.requireCustomer(request).id();
            ProfileBean updated = accountService.updateProfile(customerId, new ProfileUpdateForm(
                    request.getParameter("fullName"), request.getParameter("email"), request.getParameter("phone")));
            SessionAuth.refresh(request, before, updated);
            redirect(request, response, "/customer/profile", "Đã cập nhật hồ sơ.");
            return;
        } catch (Exception exception) { if (error(request, response, exception)) return; }
        request.setAttribute("profile", SessionAuth.requireUser(request));
        request.setAttribute("submitted", true);
        page(request, response, "Hồ sơ", "customer/profile");
    }
}
