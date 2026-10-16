package com.foodordering.servlet;

import com.foodordering.bean.AddToCartForm;
import com.foodordering.bean.CartBean;
import com.foodordering.bean.CartItemBean;
import com.foodordering.bean.CartItemOptionBean;
import com.foodordering.bean.FoodDetailBean;
import com.foodordering.bean.PromotionValidationResultBean;
import com.foodordering.bean.UpdateCartItemForm;
import com.foodordering.bean.ValidatePromotionForm;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.AppException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.BusinessException;
import com.foodordering.exception.ResourceNotFoundException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@WebServlet(name = "CartServlet", urlPatterns = {
        "/cart", "/cart/", "/cart/items", "/cart/clear", "/cart/promotion"
})
public class CartServlet extends HttpServlet {
    public static final String APPLIED_PROMOTION_CODE = "appliedPromotionCode";
    private final CartService cartService;
    private final MenuService menuService;
    private final PromotionService promotionService;

    public CartServlet() {
        this(new CartService(), new MenuService(), new PromotionService());
    }

    public CartServlet(CartService cartService, MenuService menuService, PromotionService promotionService) {
        this.cartService = cartService;
        this.menuService = menuService;
        this.promotionService = promotionService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            CartBean cart = cartService.getOrCreateCart(customerId);
            request.setAttribute("cart", cart);
            request.setAttribute("cartCount", cart.getTotalItems());
            request.setAttribute("discountAmount", BigDecimal.ZERO);
            request.setAttribute("finalSubtotal", cart.getSubtotal());
            HttpSession session = request.getSession();
            session.setAttribute("cartItemCount", cart.getTotalItems());
            transferFlash(request, session, "flashError");
            transferFlash(request, session, "flashMessage");
            Object savedCode = session.getAttribute(APPLIED_PROMOTION_CODE);
            if (savedCode instanceof String code && !code.isBlank()) {
                PromotionValidationResultBean result = promotionService.validatePromotion(
                        new ValidatePromotionForm(code, null), customerId);
                if (result.isValid() && !cart.getItems().isEmpty()) {
                    request.setAttribute("appliedPromotion", result.getPromotion());
                    request.setAttribute("discountAmount", result.getDiscountAmount());
                    request.setAttribute("finalSubtotal", result.getFinalSubtotal());
                } else {
                    session.removeAttribute(APPLIED_PROMOTION_CODE);
                    if (request.getAttribute("flashError") == null) {
                        request.setAttribute("flashError", result.getMessage());
                    }
                }
            }
            request.setAttribute("voucherCode", request.getParameter("code"));
            String editId = request.getParameter("edit");
            if (editId != null && !editId.isBlank()) {
                CartItemBean editItem = cart.getItems().stream()
                        .filter(item -> editId.equals(item.getCartItemId())).findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CART_ITEM_NOT_FOUND,
                                "Món không tồn tại trong giỏ hàng của bạn"));
                FoodDetailBean food = menuService.getFoodDetail(editItem.getFoodId(), true);
                Set<String> selectedIds = new LinkedHashSet<>();
                for (CartItemOptionBean option : editItem.getOptions()) {
                    selectedIds.add(option.getOptionId());
                }
                MenuServlet.prepareOptions(request, food, selectedIds);
                request.setAttribute("editItem", editItem);
                request.setAttribute("editFood", food);
            }
            request.setAttribute("pageTitle", "Giỏ hàng của bạn");
            request.getRequestDispatcher("/WEB-INF/views/cart/index.jsp").forward(request, response);
        } catch (AppException exception) {
            response.sendError(exception.getStatusCode(), exception.getMessage());
        } catch (BusinessException exception) {
            response.sendError(exception.getHttpStatus(), exception.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String action = request.getParameter("action");
            if ("/cart/promotion".equals(request.getServletPath())) {
                applyPromotion(request, customerId, action);
            } else if ("/cart/clear".equals(request.getServletPath()) || "clear".equals(action)) {
                storeCount(request, cartService.clearCart(customerId));
                request.getSession().removeAttribute(APPLIED_PROMOTION_CODE);
                request.getSession().setAttribute("flashMessage", "Đã xóa toàn bộ giỏ hàng.");
            } else if ("remove".equals(action)) {
                storeCount(request, cartService.removeItem(customerId, request.getParameter("cartItemId")));
                request.getSession().setAttribute("flashMessage", "Đã xóa món khỏi giỏ hàng.");
            } else if ("update".equals(action)) {
                UpdateCartItemForm form = new UpdateCartItemForm(request.getParameter("cartItemId"),
                        quantity(request), request.getParameter("note"));
                if ("true".equals(request.getParameter("customization"))) {
                    form.setOptionIds(optionIds(request));
                }
                storeCount(request, cartService.updateItem(customerId, form));
                request.getSession().setAttribute("flashMessage", "Đã cập nhật món ăn.");
            } else if (action == null || action.isBlank() || "add".equals(action)) {
                storeCount(request, cartService.addItem(customerId, new AddToCartForm(request.getParameter("foodId"),
                        quantity(request), request.getParameter("note"), optionIds(request))));
                request.getSession().setAttribute("flashMessage", "Đã thêm món vào giỏ hàng.");
            } else {
                throw new BadRequestException(ErrorCode.BAD_REQUEST, "Thao tác giỏ hàng không hợp lệ.");
            }
        } catch (AppException exception) {
            if (exception.getStatusCode() == HttpServletResponse.SC_UNAUTHORIZED
                    || exception.getStatusCode() == HttpServletResponse.SC_FORBIDDEN) {
                response.sendError(exception.getStatusCode(), exception.getMessage());
                return;
            }
            request.getSession().setAttribute("flashError", exception.getMessage());
        } catch (BusinessException exception) {
            request.getSession().setAttribute("flashError", exception.getMessage());
        }
        response.sendRedirect(request.getContextPath() + returnPath(request));
    }

    private void applyPromotion(HttpServletRequest request, String customerId, String action) {
        if ("remove".equals(action)) {
            request.getSession().removeAttribute(APPLIED_PROMOTION_CODE);
            request.getSession().setAttribute("flashMessage", "Đã bỏ mã khuyến mãi.");
            return;
        }
        PromotionValidationResultBean result = promotionService.validatePromotion(
                new ValidatePromotionForm(request.getParameter("code"), null), customerId);
        if (result.isValid()) {
            request.getSession().setAttribute(APPLIED_PROMOTION_CODE, result.getPromotion().getCode());
            request.getSession().setAttribute("flashMessage", result.getMessage());
        } else {
            request.getSession().setAttribute("flashError", result.getMessage());
        }
    }

    private static int quantity(HttpServletRequest request) {
        String value = request.getParameter("quantity");
        if (value == null || value.isBlank()) return 1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món phải là số nguyên.");
        }
    }

    private static void storeCount(HttpServletRequest request, CartBean cart) {
        if (cart != null) request.getSession().setAttribute("cartItemCount", cart.getTotalItems());
    }

    private static String returnPath(HttpServletRequest request) {
        String target = request.getParameter("returnTo");
        String context = request.getContextPath();
        if (target != null && context != null && !context.isEmpty() && target.startsWith(context + "/")) {
            target = target.substring(context.length());
        }
        if (target != null && !target.contains("\\") && !target.contains("\r") && !target.contains("\n")
                && (target.equals("/menu") || target.startsWith("/menu?") || target.startsWith("/menu/detail?"))) {
            return target;
        }
        return "/cart";
    }

    private static List<String> optionIds(HttpServletRequest request) {
        List<String> options = new ArrayList<>();
        for (String name : List.of("optionIds", "option_SIZE", "option_SUGAR_LEVEL", "option_ICE_LEVEL")) {
            String[] values = request.getParameterValues(name);
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.isBlank()) options.add(value);
                }
            }
        }
        return options;
    }

    private static void transferFlash(HttpServletRequest request, HttpSession session, String key) {
        Object value = session.getAttribute(key);
        if (value != null) {
            request.setAttribute(key, value);
            session.removeAttribute(key);
        }
    }
}
