package com.foodordering.servlet;

import com.foodordering.dto.CartDto;
import com.foodordering.dto.CartItemDto;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import com.foodordering.service.PromotionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@WebServlet(name = "cartPageServlet", urlPatterns = {"/cart", "/cart/"})
public class CartPageServlet extends HttpServlet {
    private final CartService cartService;
    private final PromotionService promotionService;
    private final MenuService menuService;

    public CartPageServlet() {
        this(new CartService(), new PromotionService(), new MenuService());
    }

    public CartPageServlet(CartService cartService, PromotionService promotionService, MenuService menuService) {
        this.cartService = cartService;
        this.promotionService = promotionService;
        this.menuService = menuService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String customerId;
        try {
            customerId = SessionAuth.requireCustomer(request).id();
        } catch (AccountException exception) {
            if (exception.getStatusCode() == 401) {
                response.sendRedirect(request.getContextPath() + "/auth/login?returnTo=/cart");
            } else {
                response.sendError(exception.getStatusCode(), exception.getMessage());
            }
            return;
        }
        HttpSession session = request.getSession();
        request.setAttribute("pageTitle", "Giỏ hàng của bạn");
        try {
            CartDto cart = cartService.getOrCreateCart(customerId);
            request.setAttribute("cart", cart);
            session.setAttribute("cartItemCount", cart.getTotalItems());
            request.setAttribute("cartTotal", cart.getSubtotal());
            String code = request.getParameter("code");
            if (code == null) code = request.getParameter("voucher");
            if (code == null) code = (String) session.getAttribute("selectedPromotionCode");
            if (code != null && !code.isBlank()) {
                PromotionValidationResultDto voucher = promotionService.validatePromotion(
                        new ValidatePromotionRequest(code, null), customerId);
                if (voucher.isValid() && !cart.getItems().isEmpty()) {
                    request.setAttribute("appliedVoucher", voucher);
                    request.setAttribute("cartTotal", cart.getSubtotal().subtract(voucher.getDiscountAmount()).max(BigDecimal.ZERO));
                    session.setAttribute("selectedPromotionId", voucher.getPromotion().getPromotionId());
                    session.setAttribute("selectedPromotionCode", voucher.getPromotion().getCode());
                } else {
                    session.removeAttribute("selectedPromotionId");
                    session.removeAttribute("selectedPromotionCode");
                    if (!cart.getItems().isEmpty()) request.setAttribute("voucherErrorMessage", voucher.getMessage());
                }
            }
            try {
                request.setAttribute("promotions", promotionService.getActivePromotions());
            } catch (RuntimeException exception) {
                request.setAttribute("promotionsError", "Không thể tải danh sách khuyến mãi.");
            }
            String editingId = request.getParameter("edit");
            if (editingId != null) {
                for (CartItemDto item : cart.getItems()) {
                    if (!editingId.equals(item.getCartItemId())) continue;
                    request.setAttribute("editingItem", item);
                    try {
                        FoodDetailResponse food = menuService.getFoodDetail(item.getFoodId(), true);
                        request.setAttribute("editingFood", food);
                        List<String> selectedOptions = new ArrayList<>();
                        item.getOptions().forEach(option -> selectedOptions.add(option.getOptionId()));
                        request.setAttribute("editingOptionIds", selectedOptions);
                        List<String> availableOptions = new ArrayList<>();
                        food.options().forEach(option -> availableOptions.add(option.id()));
                        request.setAttribute("editorMissingOptions", !availableOptions.containsAll(selectedOptions));
                        request.setAttribute("optionTypes", List.of("SIZE", "TOPPING", "SUGAR_LEVEL", "ICE_LEVEL", "OTHER"));
                    } catch (RuntimeException exception) {
                        request.setAttribute("editorError", exception instanceof com.foodordering.exception.AppException
                                || exception instanceof com.foodordering.exception.ApiException ? exception.getMessage()
                                : "Chưa tải được tùy chọn món. Vui lòng thử lại.");
                    }
                    break;
                }
            }
        } catch (RuntimeException exception) {
            request.setAttribute("cartLoadError", "Không thể tải thông tin giỏ hàng. Vui lòng thử lại.");
        }
        request.getRequestDispatcher("/WEB-INF/views/cart/index.jsp").forward(request, response);
    }
}
