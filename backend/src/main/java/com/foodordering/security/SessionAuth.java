package com.foodordering.security;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.exception.AccountException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class SessionAuth {
    public static final String CURRENT_USER = "currentUser";

    private SessionAuth() {
    }

    public static ProfileResponse requireUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(CURRENT_USER) instanceof ProfileResponse user)) {
            throw new AccountException("UNAUTHORIZED", "Vui lòng đăng nhập để tiếp tục.", 401);
        }
        return user;
    }

    public static ProfileResponse requireCustomer(HttpServletRequest request) {
        ProfileResponse user = requireUser(request);
        if (user.accountType() != AccountType.CUSTOMER) {
            throw new AccountException("FORBIDDEN", "Chức năng này chỉ dành cho khách hàng.", 403);
        }
        return user;
    }

    public static void store(HttpSession session, ProfileResponse user) {
        session.setAttribute(CURRENT_USER, user);
        session.setAttribute("userId", user.id());
        session.setAttribute("accountType", user.accountType().name());
        if (user.accountType() == AccountType.CUSTOMER) {
            session.setAttribute("customerId", user.id());
            session.removeAttribute("employeeId");
            session.removeAttribute("role");
        } else {
            session.setAttribute("employeeId", user.id());
            session.setAttribute("role", user.role() == null ? null : user.role().name());
            session.removeAttribute("customerId");
        }
    }
}
