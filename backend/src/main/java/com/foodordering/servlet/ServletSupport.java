package com.foodordering.servlet;

import com.foodordering.exception.AppException;
import com.foodordering.exception.BusinessException;
import com.foodordering.integration.PayosException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Small helpers for ordinary form submissions and JSP rendering. */
public final class ServletSupport {
    private ServletSupport() {}

    public static int status(Exception failure) {
        if (failure instanceof AppException application) return application.getStatusCode();
        if (failure instanceof BusinessException business) return business.getHttpStatus();
        if (failure instanceof PayosException gateway) return gateway.getStatusCode();
        return failure instanceof IllegalArgumentException ? 400 : 500;
    }

    public static String message(Exception failure) {
        if (failure instanceof AppException || failure instanceof BusinessException || failure instanceof PayosException)
            return failure.getMessage();
        if (failure instanceof IllegalArgumentException) return "Thông tin nhập chưa hợp lệ. Vui lòng kiểm tra lại.";
        return "Dịch vụ tạm thời không khả dụng. Vui lòng thử lại sau.";
    }

    public static void redirect(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    public static void forward(HttpServletRequest request, HttpServletResponse response, String view)
            throws ServletException, IOException {
        var session = request.getSession(false);
        if (session != null) {
            for (String key : new String[]{"flashMessage", "flashError"}) {
                if (request.getAttribute(key) == null) request.setAttribute(key, session.getAttribute(key));
                session.removeAttribute(key);
            }
        }
        request.getRequestDispatcher("/WEB-INF/views/" + view + ".jsp").forward(request, response);
    }

    public static Double decimal(String value) {
        if (value == null || value.isBlank()) return null;
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number)) throw new IllegalArgumentException("Invalid coordinate");
        return number;
    }
}
