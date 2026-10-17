package com.foodordering.servlet;

import com.foodordering.exception.ApiException;
import com.foodordering.exception.AppException;
import com.foodordering.gateway.PayosException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Các thao tác HTTP dùng chung cho Servlet nhận form. */
public abstract class BaseServlet extends HttpServlet {
    protected String parameter(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }
    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }
    protected void handleError(HttpServletResponse response, Exception failure) throws IOException {
        if (failure instanceof AppException problem) response.sendError(problem.getStatusCode(), problem.getMessage());
        else if (failure instanceof ApiException problem) response.sendError(problem.getHttpStatus(), problem.getMessage());
        else if (failure instanceof PayosException problem) response.sendError(problem.getStatusCode(), problem.getMessage());
        else if (failure instanceof IllegalArgumentException) response.sendError(400, "Thông tin gửi lên không hợp lệ.");
        else {
            log("Không xử lý được yêu cầu.", failure);
            response.sendError(500, "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau.");
        }
    }
}