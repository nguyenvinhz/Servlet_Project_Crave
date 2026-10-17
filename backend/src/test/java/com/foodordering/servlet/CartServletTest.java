package com.foodordering.servlet;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import com.foodordering.service.CartService;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CartServletTest {
    private final CartService carts = mock(CartService.class);
    private final CartServlet servlet = new CartServlet(carts);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);

    @BeforeEach
    void setup() {
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getSession(false)).thenReturn(session);
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("currentUser")).thenReturn(new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Customer", "customer@example.com", "0901234567", null));
    }

    @Test
    void addingUsesFormParametersAndTheSessionCustomerThenRedirects() throws Exception {
        when(request.getParameter("action")).thenReturn("add");
        when(request.getParameter("customerId")).thenReturn("KH99");
        when(request.getParameter("foodId")).thenReturn("MA01");
        when(request.getParameter("quantity")).thenReturn("2");
        when(request.getParameter("note")).thenReturn("Ít cay");
        when(request.getParameterValues("optionIds")).thenReturn(new String[]{"TC01", "TC02"});
        when(request.getParameter("returnTo")).thenReturn("/menu/detail?id=MA01");
        CartDto cart = new CartDto("GH01", "KH01");
        cart.setTotalItems(2);
        when(carts.addItem(eq("KH01"), any())).thenReturn(cart);

        servlet.doPost(request, response);

        ArgumentCaptor<AddToCartRequest> form = ArgumentCaptor.forClass(AddToCartRequest.class);
        verify(carts).addItem(eq("KH01"), form.capture());
        assertEquals("MA01", form.getValue().getFoodId());
        assertEquals(2, form.getValue().getQuantity());
        assertEquals("Ít cay", form.getValue().getNote());
        assertEquals(List.of("TC01", "TC02"), form.getValue().getOptionIds());
        verify(session).setAttribute("cartItemCount", 2);
        verify(response).sendRedirect("/crave/menu/detail?id=MA01");
        verify(request, never()).getInputStream();
    }

    @Test
    void quantityChangesKeepOptionsButEditorCanClearAllOptions() throws Exception {
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("cartItemId")).thenReturn("CI01");
        when(request.getParameter("quantity")).thenReturn("3");
        when(carts.updateItem(eq("KH01"), any())).thenReturn(new CartDto("GH01", "KH01"));

        servlet.doPost(request, response);
        when(request.getParameter("updateOptions")).thenReturn("true");
        servlet.doPost(request, response);

        ArgumentCaptor<UpdateCartItemRequest> forms = ArgumentCaptor.forClass(UpdateCartItemRequest.class);
        verify(carts, times(2)).updateItem(eq("KH01"), forms.capture());
        assertNull(forms.getAllValues().get(0).getOptionIds());
        assertEquals(List.of(), forms.getAllValues().get(1).getOptionIds());
    }

    @Test
    void invalidQuantityNeverMutatesTheCartAndKeepsAnErrorForThePage() throws Exception {
        when(request.getParameter("action")).thenReturn("add");
        when(request.getParameter("quantity")).thenReturn("two");

        servlet.doPost(request, response);

        verifyNoInteractions(carts);
        verify(session).setAttribute(eq("cartMessageType"), eq("error"));
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void guestIsRedirectedToLoginWithoutCreatingASession() throws Exception {
        when(request.getSession(false)).thenReturn(null);

        servlet.doPost(request, response);

        verifyNoInteractions(carts);
        verify(request, never()).getSession();
        verify(response).sendRedirect("/crave/auth/login?returnTo=%2Fcart");
    }

    @Test
    void postedReturnPathCannotRedirectToAnotherSite() throws Exception {
        when(request.getParameter("action")).thenReturn("clear");
        when(request.getParameter("returnTo")).thenReturn("//example.org");
        when(carts.clearCart("KH01")).thenReturn(new CartDto("GH01", "KH01"));

        servlet.doPost(request, response);

        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void unexpectedDatabaseFailureShowsSafeFlashAndPreservesCartBadge() throws Exception {
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletContext()).thenReturn(mock(ServletContext.class));
        servlet.init(config);
        when(request.getParameter("action")).thenReturn("clear");
        when(carts.clearCart("KH01")).thenThrow(new IllegalStateException("jdbc private-host password=secret"));

        servlet.doPost(request, response);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(session).setAttribute(eq("cartMessage"), message.capture());
        assertNotNull(message.getValue());
        assertFalse(message.getValue().isBlank());
        assertFalse(message.getValue().contains("secret"));
        assertFalse(message.getValue().contains("private-host"));
        verify(session).setAttribute("cartMessageType", "error");
        verify(session, never()).setAttribute(eq("cartItemCount"), any());
        verify(response).sendRedirect("/crave/cart");
    }

    @Test
    void domainCartFailureKeepsItsHelpfulValidationMessage() throws Exception {
        when(request.getParameter("action")).thenReturn("add");
        BadRequestException failure = new BadRequestException(ErrorCode.INVALID_QUANTITY, "Quantity exceeds allowed limit");
        when(carts.addItem(eq("KH01"), any())).thenThrow(failure);
        servlet.doPost(request, response);
        verify(session).setAttribute("cartMessage", failure.getMessage());
        verify(session).setAttribute("cartMessageType", "error");
        verify(response).sendRedirect("/crave/cart");
    }
}
