package com.foodordering.servlet;

import com.foodordering.dto.ApiResponse;
import com.foodordering.utils.JsonUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "AdminOrderServlet", urlPatterns = "/api/admin/orders/*")
public class AdminOrderServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        JsonUtils.writeJson(resp, ApiResponse.error("Chức năng cập nhật trạng thái đơn sẽ được triển khai trong Ngày 2"));
    }
}
