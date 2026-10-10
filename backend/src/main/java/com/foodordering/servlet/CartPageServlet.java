package com.foodordering.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "cartPageServlet", urlPatterns = {"/cart", "/cart/"})
public class CartPageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("pageTitle", "Giỏ hàng của bạn");
        request.getRequestDispatcher("/WEB-INF/views/cart/index.jsp").forward(request, response);
    }
}
