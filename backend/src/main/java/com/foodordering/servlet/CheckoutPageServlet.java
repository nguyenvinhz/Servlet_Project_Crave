package com.foodordering.servlet;

import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.exception.AppException;
import com.foodordering.exception.ApiException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import com.foodordering.service.CartService;
import com.foodordering.service.DeliveryFeeService;
import com.foodordering.service.PromotionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;

@WebServlet(name = "CheckoutPageServlet", urlPatterns = "/checkout")
public class CheckoutPageServlet extends BaseServlet {
    private final CartService carts = new CartService();
    private final AccountService accounts = new AccountService();
    private final PromotionService promotions = new PromotionService();
    private final DeliveryFeeService fees = new DeliveryFeeService();
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            var cart = carts.getOrCreateCart(customerId);
            var addresses = accounts.listAddresses(customerId);
            request.setAttribute("cart", cart);
            request.setAttribute("addresses", addresses);
            addresses.stream().filter(address -> address.isDefault()).findFirst()
                    .ifPresent(address -> request.setAttribute("defaultAddress", address.addressLine()));
            BigDecimal subtotal = carts.getVerifiedSubtotal(customerId);
            BigDecimal discount = BigDecimal.ZERO;
            String code = (String) request.getSession().getAttribute("selectedPromotionCode");
            if (code != null) {
                try {
                    var promotion = promotions.validatePromotion(new ValidatePromotionRequest(code, subtotal), customerId);
                    if (promotion.isValid()) {
                        discount = promotion.getDiscountAmount();
                        request.getSession().setAttribute("selectedPromotionId", promotion.getPromotion().getPromotionId());
                    } else {
                        request.getSession().removeAttribute("selectedPromotionId");
                        request.getSession().removeAttribute("selectedPromotionCode");
                        request.setAttribute("checkoutWarning", promotion.getMessage());
                    }
                } catch (AppException | ApiException invalid) {
                    request.getSession().removeAttribute("selectedPromotionId");
                    request.getSession().removeAttribute("selectedPromotionCode");
                    request.setAttribute("checkoutWarning", invalid.getMessage());
                }
            }
            request.setAttribute("subtotal", subtotal);
            request.setAttribute("discount", discount);
            request.setAttribute("store", fees.policy().store());
            request.setAttribute("deliveryConfigured", fees.policy().configured());
            request.getRequestDispatcher("/WEB-INF/views/order/checkout.jsp").forward(request, response);
        } catch (RuntimeException failure) { handleError(response, failure); }
    }
}
