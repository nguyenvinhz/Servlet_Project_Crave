package com.foodordering.servlet;

import com.foodordering.service.PromotionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "promotionPageServlet", urlPatterns = {"/promotions", "/promotions/"})
public class PromotionPageServlet extends HttpServlet {
    private final PromotionService promotionService;

    public PromotionPageServlet() {
        this(new PromotionService());
    }

    public PromotionPageServlet(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("pageTitle", "Ưu đãi & Khuyến mãi");
        try {
            request.setAttribute("promotions", promotionService.getActivePromotions());
        } catch (RuntimeException exception) {
            request.setAttribute("promotionsError", "Không thể tải danh sách khuyến mãi. Vui lòng thử lại.");
        }
        request.getRequestDispatcher("/WEB-INF/views/promotions/index.jsp").forward(request, response);
    }
}