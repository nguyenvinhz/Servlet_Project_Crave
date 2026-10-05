package com.foodordering.servlet;

import com.foodordering.dto.ApiResponse;
import com.foodordering.utils.JsonUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "OrderServlet", urlPatterns = "/api/orders/*")
public class OrderServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        JsonUtils.writeJson(resp, ApiResponse.error("Chức năng xem đơn hàng sẽ được triển khai trong Ngày 2"));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        JsonUtils.writeJson(resp, ApiResponse.error("Chức năng tạo đơn hàng sẽ được triển khai trong Ngày 2"));
    }
}
