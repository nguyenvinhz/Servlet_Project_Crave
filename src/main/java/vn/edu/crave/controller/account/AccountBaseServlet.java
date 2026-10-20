package vn.edu.crave.controller.account;

import vn.edu.crave.exception.AppException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Common forwarding for account forms. */
public abstract class AccountBaseServlet extends HttpServlet {
    protected void show(HttpServletRequest request, HttpServletResponse response, String page)
            throws ServletException, IOException {
        if ("auth/register".equals(page)) RegistrationSession.prepareView(request);
        response.setHeader("Cache-Control", "no-store");
        request.setAttribute("pageTitle", switch (page) {
            case "auth/login" -> "Đăng nhập";
            case "auth/register" -> "Đăng ký";
            case "auth/forgot-password" -> "Quên mật khẩu";
            case "auth/change-password" -> "Đổi mật khẩu";
            case "customer/profile" -> "Hồ sơ";
            default -> "Địa chỉ giao hàng";
        });
        request.getRequestDispatcher("/WEB-INF/views/" + page + ".jsp").forward(request, response);
    }

    protected void formError(HttpServletRequest request, HttpServletResponse response,
                             String page, Exception exception) throws ServletException, IOException {
        if (exception instanceof AppException app && app.getStatusCode() == 401) {
            response.sendRedirect(request.getContextPath() + "/auth/login?reason=session-expired");
            return;
        }
        if (exception instanceof AppException app) {
            response.setStatus(app.getStatusCode());
            request.setAttribute("error", app.getMessage());
            request.setAttribute("fieldErrors", app.getDetails());
        } else {
            getServletContext().log("Account form failed", exception);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute("error", "Không thể thực hiện yêu cầu. Vui lòng thử lại.");
        }
        show(request, response, page);
    }
}
