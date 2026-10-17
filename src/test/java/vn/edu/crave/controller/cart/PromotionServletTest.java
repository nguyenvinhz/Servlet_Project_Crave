package vn.edu.crave.controller.cart;

import vn.edu.crave.controller.BaseServlet;
import vn.edu.crave.controller.HealthServlet;
import vn.edu.crave.controller.account.*;
import vn.edu.crave.controller.menu.*;
import vn.edu.crave.controller.cart.*;
import vn.edu.crave.controller.order.*;
import vn.edu.crave.controller.payment.*;
import vn.edu.crave.controller.admin.*;


import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.viewmodel.PromotionDto;
import vn.edu.crave.model.viewmodel.PromotionValidationResultDto;
import vn.edu.crave.model.form.ValidatePromotionRequest;
import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.model.service.PromotionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PromotionServletTest {
    private final PromotionService promotions = mock(PromotionService.class);
    private final PromotionServlet servlet = new PromotionServlet(promotions);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);

    @BeforeEach
    void setup() {
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getServletPath()).thenReturn("/promotions/apply");
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Customer", "customer@example.com", "0901234567", null));
    }

    @Test
    void validCodeIsVerifiedAgainstTheSessionCartAndStoredForCheckout() throws Exception {
        when(request.getParameter("code")).thenReturn("SAVE10");
        when(request.getParameter("subtotal")).thenReturn("999999");
        when(request.getParameter("returnTo")).thenReturn("/checkout");
        PromotionDto promotion = new PromotionDto();
        promotion.setPromotionId("KM01");
        promotion.setCode("SAVE10");
        PromotionValidationResultDto valid = new PromotionValidationResultDto();
        valid.setValid(true);
        valid.setPromotion(promotion);
        when(promotions.validatePromotion(any(ValidatePromotionRequest.class), eq("KH01"))).thenReturn(valid);

        servlet.doPost(request, response);

        ArgumentCaptor<ValidatePromotionRequest> form = ArgumentCaptor.forClass(ValidatePromotionRequest.class);
        verify(promotions).validatePromotion(form.capture(), eq("KH01"));
        assertEquals("SAVE10", form.getValue().getCode());
        assertNull(form.getValue().getSubtotal());
        verify(session).setAttribute("selectedPromotionId", "KM01");
        verify(session).setAttribute("selectedPromotionCode", "SAVE10");
        verify(response).sendRedirect("/crave/checkout");
    }

    @Test
    void invalidCodeKeepsTheExistingSelectionAndDisplaysTheFailure() throws Exception {
        when(promotions.validatePromotion(any(ValidatePromotionRequest.class), eq("KH01")))
                .thenReturn(new PromotionValidationResultDto(false, "Không đủ điều kiện", "INVALID"));

        servlet.doPost(request, response);

        verify(session).setAttribute("voucherErrorMessage", "Không đủ điều kiện");
        verify(session, never()).setAttribute(eq("selectedPromotionId"), any());
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void removeClearsBothSessionValuesWithoutRevalidating() throws Exception {
        when(request.getServletPath()).thenReturn("/promotions/remove");

        servlet.doPost(request, response);

        verify(session).removeAttribute("selectedPromotionId");
        verify(session).removeAttribute("selectedPromotionCode");
        verifyNoInteractions(promotions);
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void guestCannotValidateOrSelectAPromotion() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        servlet.doPost(request, response);

        verifyNoInteractions(promotions);
        verify(request, never()).getSession();
        verify(response).sendRedirect("/crave/auth/login?returnTo=/cart");
    }
}