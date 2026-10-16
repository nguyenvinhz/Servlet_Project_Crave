package com.foodordering.servlet.admin;

import com.foodordering.exception.AppException;
import com.foodordering.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;

/** Small form helpers; all input comes from ordinary servlet parameters. */
final class AdminForms {
    private AdminForms() { }

    static String text(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }

    static BigDecimal amount(HttpServletRequest request, String name, boolean optional) {
        String value = text(request, name);
        if (optional && value.isEmpty()) return null;
        try { return new BigDecimal(value); }
        catch (NumberFormatException exception) { throw new ValidationException("Số tiền không hợp lệ."); }
    }

    static <E extends Enum<E>> E choice(HttpServletRequest request, String name, Class<E> type) {
        try { return Enum.valueOf(type, text(request, name).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new ValidationException("Vui lòng chọn " + name + " hợp lệ."); }
    }

    static void flash(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            request.setAttribute("adminMessage", session.getAttribute("adminMessage"));
            request.setAttribute("adminError", session.getAttribute("adminError"));
            session.removeAttribute("adminMessage");
            session.removeAttribute("adminError");
        }
    }

    static void result(HttpServletRequest request, HttpServletResponse response, String destination, Exception error)
            throws IOException {
        if (error == null) request.getSession().setAttribute("adminMessage", "Đã lưu thay đổi.");
        else {
            String message = error instanceof AppException ? error.getMessage() : "Không thể lưu thay đổi. Vui lòng thử lại.";
            request.getSession().setAttribute("adminError", message);
            if (!(error instanceof AppException)) request.getServletContext().log("Admin form failed", error);
        }
        response.sendRedirect(request.getContextPath() + destination);
    }
}
