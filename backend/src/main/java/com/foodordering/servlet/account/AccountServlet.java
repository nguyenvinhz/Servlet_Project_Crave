package com.foodordering.servlet.account;

import com.foodordering.exception.AppException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Shared HTML page handling for the account forms. */
public abstract class AccountServlet extends HttpServlet {
    protected void page(HttpServletRequest request, HttpServletResponse response, String title, String jsp)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        request.setAttribute("pageTitle", title);
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object message = session.getAttribute("accountMessage");
            if (message != null) {
                request.setAttribute("message", message);
                session.removeAttribute("accountMessage");
            }
        }
        request.getRequestDispatcher("/WEB-INF/views/" + jsp + ".jsp").forward(request, response);
    }

    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path, String message)
            throws IOException {
        if (message != null) request.getSession().setAttribute("accountMessage", message);
        response.sendRedirect(request.getContextPath() + path);
    }

    protected void loginPage(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        String returnTo = request.getParameter("returnTo");
        String query = returnTo == null || returnTo.isBlank() ? ""
                : "?returnTo=" + URLEncoder.encode(returnTo, StandardCharsets.UTF_8);
        redirect(request, response, "/auth/login" + query, message);
    }

    protected boolean error(HttpServletRequest request, HttpServletResponse response, Exception exception)
            throws IOException {
        if (exception instanceof AppException application) {
            if (application.getStatusCode() == 401) {
                redirect(request, response, "/auth/login?reason=session-expired", null);
                return true;
            }
            if (application.getStatusCode() == 403) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, application.getMessage());
                return true;
            }
            request.setAttribute("error", application.getMessage());
            request.setAttribute("fieldErrors", application.getDetails());
        } else {
            log("Unable to process account form", exception);
            request.setAttribute("error", "Không thể thực hiện yêu cầu. Vui lòng thử lại.");
        }
        return false;
    }
}
