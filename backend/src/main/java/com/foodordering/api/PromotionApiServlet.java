package com.foodordering.api;

import com.foodordering.service.PromotionService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Day 1 route and JSON contract; promotion operations are implemented on Day 2. */
@WebServlet(name = "PromotionApiServlet", urlPatterns = {"/api/promotions", "/api/promotions/*"})
public class PromotionApiServlet extends BaseApiServlet {
    public PromotionApiServlet() {
    }

    public PromotionApiServlet(PromotionService promotionService) {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Promotion retrieval");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Promotion validation");
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        methodNotAllowed(response, "GET, POST");
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        methodNotAllowed(response, "GET, POST");
    }
}
