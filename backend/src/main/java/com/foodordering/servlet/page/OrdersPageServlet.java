package com.foodordering.servlet.page;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(name = "OrdersPageServlet", urlPatterns = "/orders/*")
public class OrdersPageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.length() > 1) {
            req.getRequestDispatcher("/WEB-INF/views/order/order-detail.jsp").forward(req, resp);
        } else {
            req.getRequestDispatcher("/WEB-INF/views/order/orders.jsp").forward(req, resp);
        }
    }
}
