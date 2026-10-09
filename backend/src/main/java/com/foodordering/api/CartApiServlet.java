package com.foodordering.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.ApiError;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.ApiException;
import com.foodordering.exception.AppException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.service.CartService;
import com.foodordering.utils.JsonProvider;
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

    private final CartService cartService;

    public CartApiServlet() {
        this(new CartService());
    }

    public CartApiServlet(CartService cartService) {
        this.cartService = cartService != null ? cartService : new CartService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String customerId = resolveCustomerId(req);
            if (customerId == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để xem giỏ hàng", HttpServletResponse.SC_UNAUTHORIZED);
            }
            CartDto cart = cartService.getOrCreateCart(customerId);
            writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse<>(true, "Lấy giỏ hàng thành công", cart, null));
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String customerId = resolveCustomerId(req);
            if (customerId == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để thao tác giỏ hàng", HttpServletResponse.SC_UNAUTHORIZED);
            }

            String normPath = normalizePath(req.getPathInfo());
            String action = req.getParameter("action");

            if ("/items/update".equals(normPath) || "update".equalsIgnoreCase(action)) {
                handleUpdateItem(req, resp, customerId);
            } else if ("/items/remove".equals(normPath) || normPath.startsWith("/items/remove/") || "remove".equalsIgnoreCase(action)) {
                handleRemoveItem(req, resp, customerId);
            } else if ("/clear".equals(normPath) || "clear".equalsIgnoreCase(action)) {
                handleClearCart(req, resp, customerId);
            } else if ("".equals(normPath) || "/items".equals(normPath) || "add".equalsIgnoreCase(action)) {
                handleAddItem(req, resp, customerId);
            } else {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Đường dẫn không hợp lệ: " + req.getPathInfo());
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String customerId = resolveCustomerId(req);
            if (customerId == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để thao tác giỏ hàng", HttpServletResponse.SC_UNAUTHORIZED);
            }

            String normPath = normalizePath(req.getPathInfo());
            if ("".equals(normPath) || "/items".equals(normPath) || normPath.startsWith("/items/")) {
                handleUpdateItem(req, resp, customerId);
            } else {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Đường dẫn không hợp lệ cho thao tác PUT: " + req.getPathInfo());
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String customerId = resolveCustomerId(req);
            if (customerId == null) {
                throw new ApiException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập để thao tác giỏ hàng", HttpServletResponse.SC_UNAUTHORIZED);
            }

            String normPath = normalizePath(req.getPathInfo());
            String action = req.getParameter("action");

            if ("/clear".equals(normPath) || "clear".equalsIgnoreCase(action)) {
                handleClearCart(req, resp, customerId);
            } else if (normPath.startsWith("/items")) {
                // DELETE /api/cart/items hoặc /api/cart/items/{id}: xóa 1 món, thiếu ID phải báo lỗi 400
                handleRemoveItem(req, resp, customerId);
            } else if ("".equals(normPath)) {
                // DELETE /api/cart: nếu có cung cấp cartItemId thì xóa món, không có thì xóa toàn bộ giỏ
                String cartItemId = extractCartItemId(req, req.getPathInfo());
                if (cartItemId != null && !cartItemId.trim().isEmpty()) {
                    handleRemoveItem(req, resp, customerId);
                } else {
                    handleClearCart(req, resp, customerId);
                }
            } else {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Đường dẫn không hợp lệ cho thao tác DELETE: " + req.getPathInfo());
            }
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private void handleAddItem(HttpServletRequest req, HttpServletResponse resp, String customerId) throws IOException {
        AddToCartRequest addReq = readAddToCartDto(req);
        CartDto cart = cartService.addItem(customerId, addReq);
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse<>(true, "Thêm món vào giỏ hàng thành công", cart, null));
    }

    private void handleUpdateItem(HttpServletRequest req, HttpServletResponse resp, String customerId) throws IOException {
        UpdateCartItemRequest updateReq = readUpdateCartItemDto(req);
        CartDto cart = cartService.updateItem(customerId, updateReq);
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse<>(true, "Cập nhật món trong giỏ hàng thành công", cart, null));
    }

    private void handleRemoveItem(HttpServletRequest req, HttpServletResponse resp, String customerId) throws IOException {
        String cartItemId = extractCartItemId(req, req.getPathInfo());
        if (cartItemId == null || cartItemId.trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "Mã mục giỏ hàng (cartItemId) không được để trống khi xóa món");
        }
        CartDto cart = cartService.removeItem(customerId, cartItemId.trim());
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse<>(true, "Xóa món khỏi giỏ hàng thành công", cart, null));
    }

    private void handleClearCart(HttpServletRequest req, HttpServletResponse resp, String customerId) throws IOException {
        CartDto cart = cartService.clearCart(customerId);
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse<>(true, "Xóa toàn bộ giỏ hàng thành công", cart, null));
    }

    private String resolveCustomerId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        String customerId = session != null ? (String) session.getAttribute("customerId") : null;
        if (customerId == null || customerId.trim().isEmpty()) {
            customerId = req.getHeader("X-Customer-Id");
        }
        if (customerId == null || customerId.trim().isEmpty()) {
            customerId = req.getParameter("customerId");
        }
        return (customerId != null && !customerId.trim().isEmpty()) ? customerId.trim() : null;
    }

    private String normalizePath(String pathInfo) {
        if (pathInfo == null || pathInfo.trim().isEmpty() || pathInfo.equals("/")) {
            return "";
        }
        String normalized = pathInfo.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase();
    }

    private String getCachedRequestBody(HttpServletRequest req) throws IOException {
        String cached = (String) req.getAttribute("CACHED_REQUEST_BODY");
        if (cached != null) {
            return cached;
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        cached = sb.toString();
        req.setAttribute("CACHED_REQUEST_BODY", cached);
        return cached;
    }

    private AddToCartRequest readAddToCartDto(HttpServletRequest req) throws IOException {
        String body = getCachedRequestBody(req);
        AddToCartRequest dto = null;
        if (body != null && !body.trim().isEmpty()) {
            try {
                dto = JsonUtils.toDTO(body, AddToCartRequest.class);
            } catch (Exception e) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Dữ liệu JSON thêm món không hợp lệ: " + e.getMessage());
            }
        }
        if (dto != null && dto.getFoodId() != null && !dto.getFoodId().trim().isEmpty()) {
            return dto;
        }

        String foodId = req.getParameter("foodId");
        if (foodId == null && dto != null) {
            foodId = dto.getFoodId();
        }

        int qty = 1;
        if (req.getParameter("quantity") != null) {
            try {
                qty = Integer.parseInt(req.getParameter("quantity"));
            } catch (NumberFormatException e) {
                throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món phải là số nguyên");
            }
        } else if (dto != null && dto.getQuantity() != 0) {
            qty = dto.getQuantity();
        }

        String note = req.getParameter("note") != null ? req.getParameter("note") : (dto != null ? dto.getNote() : null);
        List<String> optionIds = req.getParameterValues("optionIds") != null
                ? List.of(req.getParameterValues("optionIds"))
                : (dto != null && dto.getOptionIds() != null ? dto.getOptionIds() : Collections.emptyList());

        return new AddToCartRequest(foodId, qty, note, optionIds);
    }

    private UpdateCartItemRequest readUpdateCartItemDto(HttpServletRequest req) throws IOException {
        String body = getCachedRequestBody(req);
        UpdateCartItemRequest dto = null;
        if (body != null && !body.trim().isEmpty()) {
            try {
                dto = JsonUtils.toDTO(body, UpdateCartItemRequest.class);
            } catch (Exception e) {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Dữ liệu JSON cập nhật không hợp lệ: " + e.getMessage());
            }
        }
        if (dto != null && dto.getCartItemId() != null && !dto.getCartItemId().trim().isEmpty()) {
            return dto;
        }

        String cartItemId = req.getParameter("cartItemId");
        if (cartItemId == null || cartItemId.trim().isEmpty()) {
            cartItemId = extractCartItemId(req, req.getPathInfo());
        }
        if (cartItemId == null && dto != null) {
            cartItemId = dto.getCartItemId();
        }

        int quantity = 1;
        if (req.getParameter("quantity") != null) {
            try {
                quantity = Integer.parseInt(req.getParameter("quantity"));
            } catch (NumberFormatException e) {
                throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món phải là số nguyên");
            }
        } else if (dto != null) {
            quantity = dto.getQuantity();
        }

        String note = req.getParameter("note") != null ? req.getParameter("note") : (dto != null ? dto.getNote() : null);
        return new UpdateCartItemRequest(cartItemId, quantity, note);
    }

    private String extractCartItemId(HttpServletRequest req, String pathInfo) throws IOException {
        String id = req.getParameter("cartItemId");
        if (id != null && !id.trim().isEmpty()) {
            return id.trim();
        }
        id = req.getParameter("id");
        if (id != null && !id.trim().isEmpty()) {
            return id.trim();
        }

        if (pathInfo != null) {
            String trimmed = pathInfo.replaceAll("^/+", "").replaceAll("/+$", "");
            String[] parts = trimmed.split("/");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase("items")) {
                if (parts.length == 2 && !parts[1].equalsIgnoreCase("remove") && !parts[1].equalsIgnoreCase("update")) {
                    return parts[1].trim();
                } else if (parts.length >= 3 && parts[1].equalsIgnoreCase("remove")) {
                    return parts[2].trim();
                }
            }
        }

        String body = getCachedRequestBody(req);
        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonNode node = JsonProvider.objectMapper().readTree(body);
                if (node != null) {
                    if (node.has("cartItemId") && !node.get("cartItemId").isNull()) {
                        String val = node.get("cartItemId").asText();
                        if (val != null && !val.trim().isEmpty()) {
                            return val.trim();
                        }
                    }
                    if (node.has("id") && !node.get("id").isNull()) {
                        String val = node.get("id").asText();
                        if (val != null && !val.trim().isEmpty()) {
                            return val.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void handleError(HttpServletResponse resp, Exception e) throws IOException {
        int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        String message = e.getMessage() != null ? e.getMessage() : "Lỗi hệ thống máy chủ nội bộ";

        if (e instanceof ApiException apiEx) {
            statusCode = apiEx.getHttpStatus();
            errorCode = apiEx.getErrorCode() != null ? apiEx.getErrorCode() : ErrorCode.BAD_REQUEST;
            message = apiEx.getMessage();
        } else if (e instanceof AppException appEx) {
            statusCode = appEx.getStatusCode();
            writeResponse(resp, statusCode, ApiResponse.failure(new ApiError(appEx.getErrorCode(), appEx.getMessage(), appEx.getDetails())));
            return;
        } else if (e instanceof com.fasterxml.jackson.core.JsonProcessingException || e instanceof IllegalArgumentException) {
            statusCode = HttpServletResponse.SC_BAD_REQUEST;
            errorCode = ErrorCode.BAD_REQUEST;
            message = "Dữ liệu JSON không hợp lệ: " + e.getMessage();
        } else if (e.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            statusCode = HttpServletResponse.SC_BAD_REQUEST;
            errorCode = ErrorCode.BAD_REQUEST;
            message = "Dữ liệu JSON không hợp lệ: " + e.getCause().getMessage();
        }

        writeResponse(resp, statusCode, new ApiResponse<>(false, message, null, errorCode));
    }

    private void writeResponse(HttpServletResponse resp, int statusCode, ApiResponse<?> apiResponse) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(statusCode);
        resp.getWriter().write(JsonUtils.toJson(apiResponse));
        resp.getWriter().flush();
    }
}
