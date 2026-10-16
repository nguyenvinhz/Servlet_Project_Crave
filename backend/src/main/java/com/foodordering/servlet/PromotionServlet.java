package com.foodordering.servlet;

import com.foodordering.service.PromotionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "PromotionServlet", urlPatterns = {"/promotions", "/promotions/"})
public class PromotionServlet extends HttpServlet {
    private final PromotionService promotionService;

    public PromotionServlet() {
        this(new PromotionService());
    }

    public PromotionServlet(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("promotions", promotionService.getActivePromotions());
        request.setAttribute("pageTitle", "Ưu đãi & Khuyến mãi");
        request.getRequestDispatcher("/WEB-INF/views/promotions/index.jsp").forward(request, response);
    }
}
