package com.foodordering.servlet;

import com.foodordering.bean.AddToCartForm;
import com.foodordering.bean.CartBean;
import com.foodordering.bean.CartItemBean;
import com.foodordering.bean.CartItemOptionBean;
import com.foodordering.bean.FoodDetailBean;
import com.foodordering.bean.ProfileBean;
import com.foodordering.bean.PromotionBean;
import com.foodordering.bean.PromotionValidationResultBean;
import com.foodordering.bean.UpdateCartItemForm;
import com.foodordering.bean.ValidatePromotionForm;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import com.foodordering.service.PromotionService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CartServletTest {
    private CartService carts;
    private MenuService menu;
    private PromotionService promotions;
    private CartServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private Map<String, Object> sessionValues;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        carts = mock(CartService.class);
        menu = mock(MenuService.class);
        promotions = mock(PromotionService.class);
        servlet = new CartServlet(carts, menu, promotions);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        sessionValues = new HashMap<>();
        attributes = new HashMap<>();
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/crave");
        when(session.getAttribute(anyString())).thenAnswer(call -> sessionValues.get(call.getArgument(0)));
        doAnswer(call -> { sessionValues.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
        doAnswer(call -> { sessionValues.remove(call.getArgument(0)); return null; })
                .when(session).removeAttribute(anyString());
        when(request.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> { attributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(request).setAttribute(anyString(), any());
        SessionAuth.store(session, new ProfileBean("customer-1", AccountType.CUSTOMER,
                "Customer", "customer@example.com", "0900000000", null));
    }

    @Test
    void addReadsFormParametersAndRedirectsWithoutReadingRequestBody() throws Exception {
        when(request.getServletPath()).thenReturn("/cart/items");
        when(request.getParameter("foodId")).thenReturn("food-1");
        when(request.getParameter("quantity")).thenReturn("2");
        when(request.getParameter("note")).thenReturn("Ít cay");
        when(request.getParameterValues("optionIds")).thenReturn(new String[]{"topping-1"});
        when(request.getParameterValues("option_SIZE")).thenReturn(new String[]{"size-1"});
        when(request.getParameterValues("option_ICE_LEVEL")).thenReturn(new String[]{""});

        servlet.doPost(request, response);

        ArgumentCaptor<AddToCartForm> form = ArgumentCaptor.forClass(AddToCartForm.class);
        verify(carts).addItem(eq("customer-1"), form.capture());
        assertEquals("food-1", form.getValue().getFoodId());
        assertEquals(2, form.getValue().getQuantity());
        assertEquals("Ít cay", form.getValue().getNote());
        assertEquals(List.of("topping-1", "size-1"), form.getValue().getOptionIds());
        verify(request, never()).getReader();
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void quantityChangeKeepsExistingCustomizationAndNote() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("cartItemId")).thenReturn("item-1");
        when(request.getParameter("quantity")).thenReturn("0");

        servlet.doPost(request, response);

        ArgumentCaptor<UpdateCartItemForm> form = ArgumentCaptor.forClass(UpdateCartItemForm.class);
        verify(carts).updateItem(eq("customer-1"), form.capture());
        assertEquals(0, form.getValue().getQuantity());
        assertNull(form.getValue().getOptionIds());
        assertNull(form.getValue().getNote());
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void customizationCanRemoveAllOptions() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("customization")).thenReturn("true");
        when(request.getParameter("cartItemId")).thenReturn("item-1");
        when(request.getParameter("quantity")).thenReturn("1");

        servlet.doPost(request, response);

        ArgumentCaptor<UpdateCartItemForm> form = ArgumentCaptor.forClass(UpdateCartItemForm.class);
        verify(carts).updateItem(eq("customer-1"), form.capture());
        assertEquals(List.of(), form.getValue().getOptionIds());
    }

    @Test
    void malformedQuantityShowsErrorWithoutMutatingCart() throws Exception {
        when(request.getParameter("quantity")).thenReturn("1.5");
        servlet.doPost(request, response);
        verifyNoInteractions(carts);
        assertTrue(sessionValues.containsKey("flashError"));
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void unavailableFoodServiceErrorReturnsFeedbackInsteadOfServerError() throws Exception {
        when(carts.addItem(eq("customer-1"), any(AddToCartForm.class)))
                .thenThrow(new BadRequestException(ErrorCode.FOOD_NOT_AVAILABLE, "Món hiện tạm hết"));
        servlet.doPost(request, response);
        assertEquals("Món hiện tạm hết", sessionValues.get("flashError"));
        verify(response).sendRedirect("/crave/cart");
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void quickAddReturnsToLocalMenuAndUpdatesSessionCount() throws Exception {
        when(request.getParameter("returnTo")).thenReturn("/crave/menu?keyword=Com");
        CartBean cart = new CartBean("cart-1", "customer-1");
        cart.setTotalItems(4);
        when(carts.addItem(eq("customer-1"), any(AddToCartForm.class))).thenReturn(cart);
        servlet.doPost(request, response);
        verify(response).sendRedirect("/crave/menu?keyword=Com");
        assertEquals(4, sessionValues.get("cartItemCount"));
    }

    @Test
    void externalReturnTargetCannotChangeRedirectDestination() throws Exception {
        when(request.getParameter("returnTo")).thenReturn("//example.com/menu");
        servlet.doPost(request, response);
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void promotionUsesAuthenticatedCustomerAndStoresOnlyValidatedCode() throws Exception {
        when(request.getServletPath()).thenReturn("/cart/promotion");
        when(request.getParameter("code")).thenReturn("sale");
        PromotionBean promotion = new PromotionBean();
        promotion.setCode("SALE");
        when(promotions.validatePromotion(any(ValidatePromotionForm.class), eq("customer-1")))
                .thenReturn(new PromotionValidationResultBean(true, "Áp dụng thành công", promotion,
                        new BigDecimal("100000"), new BigDecimal("10000"), new BigDecimal("90000")));

        servlet.doPost(request, response);

        ArgumentCaptor<ValidatePromotionForm> form = ArgumentCaptor.forClass(ValidatePromotionForm.class);
        verify(promotions).validatePromotion(form.capture(), eq("customer-1"));
        assertEquals("sale", form.getValue().getCode());
        assertNull(form.getValue().getSubtotal());
        assertEquals("SALE", sessionValues.get(CartServlet.APPLIED_PROMOTION_CODE));
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void cartGetRevalidatesVoucherAndRemovesItIfCartNoLongerQualifies() throws Exception {
        CartBean cart = new CartBean("cart-1", "customer-1");
        cart.setSubtotal(new BigDecimal("50000"));
        when(carts.getOrCreateCart("customer-1")).thenReturn(cart);
        sessionValues.put(CartServlet.APPLIED_PROMOTION_CODE, "SALE");
        when(promotions.validatePromotion(any(ValidatePromotionForm.class), eq("customer-1")))
                .thenReturn(new PromotionValidationResultBean(false, "Chưa đạt giá trị tối thiểu", "MINIMUM"));
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/WEB-INF/views/cart/index.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        assertFalse(sessionValues.containsKey(CartServlet.APPLIED_PROMOTION_CODE));
        assertEquals("Chưa đạt giá trị tối thiểu", attributes.get("flashError"));
        assertEquals(cart, attributes.get("cart"));
        assertEquals(new BigDecimal("50000"), attributes.get("finalSubtotal"));
        verify(dispatcher).forward(request, response);
    }

    @Test
    void cartEditorLoadsOnlyAnItemFromAuthenticatedCustomersCart() throws Exception {
        CartBean cart = new CartBean("cart-1", "customer-1");
        CartItemBean item = new CartItemBean();
        item.setCartItemId("item-1");
        item.setFoodId("food-1");
        item.setOptions(List.of(new CartItemOptionBean("size-1", "Lớn", "SIZE", BigDecimal.ONE)));
        cart.setItems(List.of(item));
        when(carts.getOrCreateCart("customer-1")).thenReturn(cart);
        when(request.getParameter("edit")).thenReturn("item-1");
        when(menu.getFoodDetail("food-1", true)).thenReturn(new FoodDetailBean("food-1", "cat-1", "Món",
                "Cơm", BigDecimal.TEN, null, null, FoodStatus.AVAILABLE, List.of()));
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/WEB-INF/views/cart/index.jsp")).thenReturn(dispatcher);

        servlet.doGet(request, response);

        assertEquals(item, attributes.get("editItem"));
        assertEquals(Set.of("size-1"), attributes.get("selectedOptionIds"));
        verify(dispatcher).forward(request, response);
    }

    @Test
    void foreignCartItemCannotBeLoadedIntoEditor() throws Exception {
        when(carts.getOrCreateCart("customer-1")).thenReturn(new CartBean("cart-1", "customer-1"));
        when(request.getParameter("edit")).thenReturn("someone-elses-item");
        servlet.doGet(request, response);
        verify(response).sendError(eq(404), anyString());
        verifyNoInteractions(menu);
    }

    @Test
    void anonymousPostCannotMutateCart() throws Exception {
        when(request.getSession(false)).thenReturn(null);
        servlet.doPost(request, response);
        verify(response).sendError(eq(401), anyString());
        verifyNoInteractions(carts, promotions);
    }
}
