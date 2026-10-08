package com.foodordering.api;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.service.CartService;
import com.foodordering.utils.JsonUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@WebServlet(name = "CartApiServlet", urlPatterns = {"/api/cart", "/api/cart/*"})
public class CartApiServlet extends HttpServlet {

    private final CartService cartService = new CartService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String customerId = (String) session.getAttribute("customerId");
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, new ApiResponse(false, "Vui lòng đăng nhập để xem giỏ hàng", null, ErrorCode.UNAUTHORIZED));
            return;
        }
        CartDto cart = cartService.getOrCreateCart(customerId);
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse(true, "Lấy giỏ hàng thành công", cart, null));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String customerId = (String) session.getAttribute("customerId");
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, new ApiResponse(false, "Vui lòng đăng nhập để thao tác giỏ hàng", null, ErrorCode.UNAUTHORIZED));
            return;
        }
        AddToCartRequest addReq = readAddToCartDto(req);
        CartDto cart = cartService.addItem(customerId, addReq);
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse(true, "Thêm món vào giỏ hàng thành công", cart, null));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String customerId = (String) session.getAttribute("customerId");
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, new ApiResponse(false, "Vui lòng đăng nhập để thao tác giỏ hàng", null, ErrorCode.UNAUTHORIZED));
            return;
        }
        UpdateCartItemRequest updateReq = readUpdateCartItemDto(req);
        CartDto cart = cartService.updateItem(customerId, updateReq);
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse(true, "Cập nhật món trong giỏ hàng thành công", cart, null));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String customerId = (String) session.getAttribute("customerId");
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED, new ApiResponse(false, "Vui lòng đăng nhập để thao tác giỏ hàng", null, ErrorCode.UNAUTHORIZED));
            return;
        }
        String cartItemId = req.getParameter("cartItemId");
        CartDto cart;
        if (cartItemId == null || cartItemId.trim().isEmpty()) {
            cart = cartService.clearCart(customerId);
        } else {
            cart = cartService.removeItem(customerId, cartItemId);
        }
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse(true, "Xóa thành công", cart, null));
    }

    private AddToCartRequest readAddToCartDto(HttpServletRequest req) throws IOException {
        String body = readRequestBody(req);
        AddToCartRequest dto = JsonUtils.toDTO(body, AddToCartRequest.class);
        if (dto != null && dto.getFoodId() != null && !dto.getFoodId().trim().isEmpty()) {
            return dto;
        }
        String foodId = req.getParameter("foodId");
        int qty = 1;
        if (req.getParameter("quantity") != null) {
            try {
                qty = Integer.parseInt(req.getParameter("quantity"));
            } catch (NumberFormatException ignored) {
            }
        }
        String note = req.getParameter("note");
        List<String> optionIds = req.getParameterValues("optionIds") != null
                ? List.of(req.getParameterValues("optionIds"))
                : Collections.emptyList();

        return new AddToCartRequest(foodId, qty, note, optionIds);
    }

    private UpdateCartItemRequest readUpdateCartItemDto(HttpServletRequest req) throws IOException {
        String body = readRequestBody(req);
        UpdateCartItemRequest dto = JsonUtils.toDTO(body, UpdateCartItemRequest.class);
        return dto;
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void writeResponse(HttpServletResponse resp, int statusCode, ApiResponse<?> apiResponse) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(statusCode);
        resp.getWriter().write(JsonUtils.toJson(apiResponse));
        resp.getWriter().flush();
    }
}
