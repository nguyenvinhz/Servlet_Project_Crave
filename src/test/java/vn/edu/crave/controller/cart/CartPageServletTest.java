package vn.edu.crave.controller.cart;

import vn.edu.crave.controller.BaseServlet;
import vn.edu.crave.controller.HealthServlet;
import vn.edu.crave.controller.account.*;
import vn.edu.crave.controller.menu.*;
import vn.edu.crave.controller.cart.*;
import vn.edu.crave.controller.order.*;
import vn.edu.crave.controller.payment.*;
import vn.edu.crave.controller.admin.*;


import vn.edu.crave.model.viewmodel.CartDto;
import vn.edu.crave.model.viewmodel.CartItemDto;
import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.viewmodel.PromotionDto;
import vn.edu.crave.model.viewmodel.PromotionValidationResultDto;
import vn.edu.crave.model.form.ValidatePromotionRequest;
import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.model.service.CartService;
import vn.edu.crave.model.service.MenuService;
import vn.edu.crave.model.service.PromotionService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CartPageServletTest {
    private final CartService carts = mock(CartService.class);
    private final PromotionService promotions = mock(PromotionService.class);
    private final MenuService menu = mock(MenuService.class);
    private final CartPageServlet servlet = new CartPageServlet(carts, promotions, menu);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);

    @BeforeEach
    void setup() {
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Customer", "customer@example.com", "0901234567", null));
        when(request.getRequestDispatcher("/WEB-INF/views/cart/index.jsp")).thenReturn(dispatcher);
        CartDto cart = new CartDto("GH01", "KH01");
        cart.setItems(List.of(new CartItemDto()));
        cart.setSubtotal(new BigDecimal("150000"));
        cart.setTotalItems(2);
        when(carts.getOrCreateCart("KH01")).thenReturn(cart);
    }

    @Test
    void renderedCartRevalidatesSavedVoucherAndUsesTheCurrentDiscount() throws Exception {
        when(session.getAttribute("selectedPromotionCode")).thenReturn("SAVE10");
        PromotionDto promotion = new PromotionDto();
        promotion.setPromotionId("KM01");
        promotion.setCode("SAVE10");
        PromotionValidationResultDto result = new PromotionValidationResultDto(true, "OK", promotion,
                new BigDecimal("150000"), new BigDecimal("15000"), new BigDecimal("135000"));
        when(promotions.validatePromotion(any(ValidatePromotionRequest.class), eq("KH01"))).thenReturn(result);

        servlet.doGet(request, response);

        verify(request).setAttribute("appliedVoucher", result);
        verify(request).setAttribute("cartTotal", new BigDecimal("135000"));
        verify(session).setAttribute("selectedPromotionId", "KM01");
        verify(dispatcher).forward(request, response);
    }

    @Test
    void aVoucherThatNoLongerQualifiesIsClearedBeforeRendering() throws Exception {
        when(session.getAttribute("selectedPromotionCode")).thenReturn("SAVE10");
        when(promotions.validatePromotion(any(ValidatePromotionRequest.class), eq("KH01")))
                .thenReturn(new PromotionValidationResultDto(false, "Không đủ điều kiện", "INVALID"));

        servlet.doGet(request, response);

        verify(session).removeAttribute("selectedPromotionId");
        verify(session).removeAttribute("selectedPromotionCode");
        verify(request).setAttribute("voucherErrorMessage", "Không đủ điều kiện");
        verify(request, never()).setAttribute(eq("appliedVoucher"), any());
        verify(dispatcher).forward(request, response);
    }
}