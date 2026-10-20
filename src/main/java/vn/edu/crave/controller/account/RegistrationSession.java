package vn.edu.crave.controller.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.model.service.EmailVerificationService.PendingRegistration;

/** Keeps the validated registration and password hash on the server during OTP verification. */
final class RegistrationSession {
    static final String ATTRIBUTE = "pendingRegistration";

    private RegistrationSession() {
    }

    static PendingRegistration current(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof PendingRegistration pending)) {
                return null;
            }
            if (pending.getExpiresAtMillis() <= System.currentTimeMillis()) {
                copyContact(request, pending);
                session.removeAttribute(ATTRIBUTE);
                return null;
            }
            return pending;
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    static PendingRegistration require(HttpServletRequest request) {
        PendingRegistration pending = current(request);
        if (pending == null) {
            throw new AccountException("REGISTRATION_EXPIRED",
                    "Phiên đăng ký đã hết hạn. Vui lòng nhập lại thông tin để nhận mã mới.", 400);
        }
        return pending;
    }

    static void store(HttpServletRequest request, PendingRegistration pending) {
        request.getSession(true).setAttribute(ATTRIBUTE, pending);
    }

    static void clear(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) session.removeAttribute(ATTRIBUTE);
        } catch (IllegalStateException ignored) {
            // A concurrent logout already discarded the registration.
        }
    }

    static void clearIfCurrent(HttpServletRequest request, PendingRegistration pending) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                synchronized (session) {
                    if (session.getAttribute(ATTRIBUTE) == pending) session.removeAttribute(ATTRIBUTE);
                }
            }
        } catch (IllegalStateException ignored) {
            // A concurrent logout already discarded the registration.
        }
    }

    static void prepareView(HttpServletRequest request) {
        PendingRegistration pending = current(request);
        request.setAttribute("otpRequested", pending != null);
        if (pending != null) copyContact(request, pending);
    }

    static void restart(HttpServletRequest request) {
        PendingRegistration pending = current(request);
        if (pending != null) copyContact(request, pending);
        clear(request);
    }

    private static void copyContact(HttpServletRequest request, PendingRegistration pending) {
        request.setAttribute("registrationFullName", pending.getFullName());
        request.setAttribute("registrationEmail", pending.getEmail());
        request.setAttribute("registrationPhone", pending.getPhone());
    }
}
