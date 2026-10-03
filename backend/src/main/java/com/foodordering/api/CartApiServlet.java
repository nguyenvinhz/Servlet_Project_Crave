package com.foodordering.api;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.ApiException;
import com.foodordering.service.CartService;
import com.foodordering.utils.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Servlet cung cấp RESTful API cho giỏ hàng (Cart API do Ung Văn Trí phụ trách).
 * Route: /api/cart, /api/cart/*
 */
@WebServlet(name = "CartApiServlet", urlPatterns = {"/api/cart", "/api/cart/*"})
public class CartApiServlet extends HttpServlet {

    private final CartService cartService = new CartService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setupResponseHeaders(resp);
        String customerId = resolveCustomerId(req);
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    ApiResponse.error(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để xem giỏ hàng"));
            return;
        }

        try {
            CartDto cart = cartService.getOrCreateCart(customerId);
            writeResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success("Lấy giỏ hàng thành công", cart));
        } catch (ApiException e) {
            writeResponse(resp, e.getHttpStatus(), ApiResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            writeResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi máy chủ: " + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setupResponseHeaders(resp);
        String customerId = resolveCustomerId(req);
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    ApiResponse.error(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để thao tác giỏ hàng"));
            return;
        }

        String pathInfo = req.getPathInfo(); // ví dụ: /items, /items/update, /clear
        String action = req.getParameter("action"); // hỗ trợ form submission

        String body = readRequestBody(req);
        Map<String, Object> bodyMap = JsonUtils.parseJsonObject(body);

        try {
            if ("clear".equalsIgnoreCase(action) || (pathInfo != null && pathInfo.endsWith("/clear"))) {
                // Xóa toàn bộ giỏ
                CartDto cart = cartService.clearCart(customerId);
                writeResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success("Đã làm sạch giỏ hàng", cart));
            } else if ("update".equalsIgnoreCase(action) || (pathInfo != null && pathInfo.contains("/update"))) {
                // Cập nhật số lượng món qua POST
                handleUpdateItem(req, resp, customerId, bodyMap);
            } else if ("remove".equalsIgnoreCase(action) || (pathInfo != null && pathInfo.contains("/remove"))) {
                // Xóa món qua POST
                handleRemoveItem(req, resp, customerId, bodyMap);
            } else {
                // Mặc định: thêm món vào giỏ
                handleAddItem(req, resp, customerId, bodyMap);
            }
        } catch (ApiException e) {
            writeResponse(resp, e.getHttpStatus(), ApiResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            writeResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi máy chủ: " + e.getMessage()));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setupResponseHeaders(resp);
        String customerId = resolveCustomerId(req);
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    ApiResponse.error(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để thao tác giỏ hàng"));
            return;
        }

        String body = readRequestBody(req);
        Map<String, Object> bodyMap = JsonUtils.parseJsonObject(body);

        try {
            handleUpdateItem(req, resp, customerId, bodyMap);
        } catch (ApiException e) {
            writeResponse(resp, e.getHttpStatus(), ApiResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            writeResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi máy chủ: " + e.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setupResponseHeaders(resp);
        String customerId = resolveCustomerId(req);
        if (customerId == null) {
            writeResponse(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    ApiResponse.error(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để thao tác giỏ hàng"));
            return;
        }

        String pathInfo = req.getPathInfo();
        try {
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.endsWith("/clear")) {
                // Xóa toàn bộ giỏ
                CartDto cart = cartService.clearCart(customerId);
                writeResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success("Đã xóa toàn bộ giỏ hàng", cart));
            } else {
                // Xóa một món: trích xuất cartItemId từ path (/items/{id}) hoặc query param ?cartItemId=...
                String cartItemId = req.getParameter("cartItemId");
                if (cartItemId == null && pathInfo.startsWith("/items/")) {
                    cartItemId = pathInfo.substring("/items/".length());
                }
                CartDto cart = cartService.removeItem(customerId, cartItemId);
                writeResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success("Đã xóa món khỏi giỏ hàng", cart));
            }
        } catch (ApiException e) {
            writeResponse(resp, e.getHttpStatus(), ApiResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            writeResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi máy chủ: " + e.getMessage()));
        }
    }

    private void handleAddItem(HttpServletRequest req, HttpServletResponse resp, String customerId, Map<String, Object> bodyMap) throws IOException {
        String foodId = JsonUtils.getString(bodyMap, "foodId");
        if (foodId == null) foodId = req.getParameter("foodId");

        Integer qty = JsonUtils.getInteger(bodyMap, "quantity");
        if (qty == null) {
            String qParam = req.getParameter("quantity");
            qty = qParam != null ? Integer.parseInt(qParam) : 1;
        }

        String note = JsonUtils.getString(bodyMap, "note");
        if (note == null) note = req.getParameter("note");

        List<String> optionIds = JsonUtils.getStringList(bodyMap, "optionIds");
        if (optionIds.isEmpty() && req.getParameterValues("optionIds") != null) {
            optionIds = List.of(req.getParameterValues("optionIds"));
        }

        AddToCartRequest addReq = new AddToCartRequest(foodId, qty, note, optionIds);
        CartDto updatedCart = cartService.addItem(customerId, addReq);
        writeResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success("Đã thêm món vào giỏ hàng thành công", updatedCart));
    }

    private void handleUpdateItem(HttpServletRequest req, HttpServletResponse resp, String customerId, Map<String, Object> bodyMap) throws IOException {
        String cartItemId = JsonUtils.getString(bodyMap, "cartItemId");
        if (cartItemId == null) cartItemId = req.getParameter("cartItemId");

        Integer qty = JsonUtils.getInteger(bodyMap, "quantity");
        if (qty == null && req.getParameter("quantity") != null) {
            qty = Integer.parseInt(req.getParameter("quantity"));
        }
        if (qty == null) qty = 1;

        String note = JsonUtils.getString(bodyMap, "note");
        if (note == null) note = req.getParameter("note");

        UpdateCartItemRequest updateReq = new UpdateCartItemRequest(cartItemId, qty, note);
        CartDto updatedCart = cartService.updateItem(customerId, updateReq);
        writeResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success("Đã cập nhật món trong giỏ hàng", updatedCart));
    }

    private void handleRemoveItem(HttpServletRequest req, HttpServletResponse resp, String customerId, Map<String, Object> bodyMap) throws IOException {
        String cartItemId = JsonUtils.getString(bodyMap, "cartItemId");
        if (cartItemId == null) cartItemId = req.getParameter("cartItemId");

        CartDto updatedCart = cartService.removeItem(customerId, cartItemId);
        writeResponse(resp, HttpServletResponse.SC_OK, ApiResponse.success("Đã xóa món khỏi giỏ hàng", updatedCart));
    }

    private String resolveCustomerId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            Object cid = session.getAttribute("customerId");
            if (cid != null) return cid.toString().trim();
            Object userObj = session.getAttribute("user");
            if (userObj != null) {
                // Kiểm tra nếu có phương thức getCustomerId hoặc getUserId
                try {
                    var m = userObj.getClass().getMethod("getCustomerId");
                    Object res = m.invoke(userObj);
                    if (res != null) return res.toString().trim();
                } catch (Exception ignored) {
                }
            }
        }

        // Hỗ trợ header hoặc query param phục vụ test/tích hợp API
        String headerCid = req.getHeader("X-Customer-Id");
        if (headerCid != null && !headerCid.trim().isEmpty()) {
            return headerCid.trim();
        }

        String paramCid = req.getParameter("customerId");
        if (paramCid != null && !paramCid.trim().isEmpty()) {
            return paramCid.trim();
        }

        return null;
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

    private void setupResponseHeaders(HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Customer-Id");
    }

    private void writeResponse(HttpServletResponse resp, int statusCode, ApiResponse<?> apiResponse) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write(JsonUtils.toJson(apiResponse));
        resp.getWriter().flush();
    }
}
