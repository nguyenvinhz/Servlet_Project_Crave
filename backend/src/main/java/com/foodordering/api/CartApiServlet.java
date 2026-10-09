package com.foodordering.api;

import com.foodordering.service.CartService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Day 1 route and JSON contract; cart operations are implemented on Day 2. */
@WebServlet(name = "CartApiServlet", urlPatterns = {"/api/cart", "/api/cart/*"})
public class CartApiServlet extends BaseApiServlet {
    public CartApiServlet() {
    }

    public CartApiServlet(CartService cartService) {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Cart retrieval");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Cart item creation, update, removal or cart clearing");
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Cart item update");
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        notImplemented(response, "Cart item removal or cart clearing");
    }
}
