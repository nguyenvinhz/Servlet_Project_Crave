package com.foodordering.servlet;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.AccountException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CartService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@WebServlet(name = "CartServlet", urlPatterns = "/cart/action")
public class CartServlet extends HttpServlet {
    private final CartService cartService;

    public CartServlet() {
        this(new CartService());
    }

    public CartServlet(CartService cartService) {
        this.cartService = cartService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String returnTo = returnPath(request);
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String action = request.getParameter("action");
            CartDto cart;
            String message;
            if ("add".equals(action)) {
                String[] options = request.getParameterValues("optionIds");
                AddToCartRequest item = new AddToCartRequest(request.getParameter("foodId"),
                        quantity(request), request.getParameter("note"), options == null ? List.of() : List.of(options));
                cart = cartService.addItem(customerId, item);
                message = "Đã thêm món vào giỏ hàng!";
            } else if ("update".equals(action)) {
                UpdateCartItemRequest item = new UpdateCartItemRequest(request.getParameter("cartItemId"),
                        quantity(request), request.getParameter("note"));
                if ("true".equals(request.getParameter("updateOptions"))) {
                    String[] options = request.getParameterValues("optionIds");
                    item.setOptionIds(options == null ? List.of() : List.of(options));
                }
                cart = cartService.updateItem(customerId, item);
                message = "Đã cập nhật món trong giỏ hàng";
            } else if ("remove".equals(action)) {
                cart = cartService.removeItem(customerId, request.getParameter("cartItemId"));
                message = "Đã xóa món khỏi giỏ hàng";
            } else if ("clear".equals(action)) {
                cart = cartService.clearCart(customerId);
                message = "Đã xóa toàn bộ giỏ hàng";
            } else {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Thao tác giỏ hàng không hợp lệ.");
            }
            request.getSession().setAttribute("cartItemCount", cart.getTotalItems());
            request.getSession().setAttribute("cartMessage", message);
            request.getSession().setAttribute("cartMessageType", "success");
        } catch (AccountException exception) {
            if (exception.getStatusCode() == HttpServletResponse.SC_UNAUTHORIZED) {
                response.sendRedirect(request.getContextPath() + "/auth/login?returnTo="
                        + URLEncoder.encode(returnTo, StandardCharsets.UTF_8));
            } else {
                response.sendError(exception.getStatusCode(), exception.getMessage());
            }
            return;
        } catch (RuntimeException exception) {
            boolean validation = exception instanceof com.foodordering.exception.AppException
                    || exception instanceof com.foodordering.exception.ApiException;
            if (!validation) log("Không cập nhật được giỏ hàng.", exception);
            request.getSession().setAttribute("cartMessage", validation ? exception.getMessage()
                    : "Chưa cập nhật được giỏ hàng. Vui lòng thử lại.");
            request.getSession().setAttribute("cartMessageType", "error");
        }
        response.sendRedirect(request.getContextPath() + returnTo);
    }

    private int quantity(HttpServletRequest request) {
        String value = request.getParameter("quantity");
        try {
            return value == null ? 1 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món phải là số nguyên.");
        }
    }

    private String returnPath(HttpServletRequest request) {
        String path = request.getParameter("returnTo");
        if (path != null && (path.equals("/menu") || path.startsWith("/menu?")
                || path.startsWith("/menu/detail?") || path.equals("/cart"))
                && !path.contains("\r") && !path.contains("\n")) {
            return path;
        }
        return "/cart";
    }
}
