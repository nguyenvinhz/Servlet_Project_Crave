package com.foodordering.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "promotionPageServlet", urlPatterns = {"/promotions", "/promotions/"})
public class PromotionPageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("pageTitle", "Ưu đãi & Khuyến mãi");
        request.getRequestDispatcher("/WEB-INF/views/promotions/index.jsp").forward(request, response);
    }
}
