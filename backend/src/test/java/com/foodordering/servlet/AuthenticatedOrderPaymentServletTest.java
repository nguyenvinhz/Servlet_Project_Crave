package com.foodordering.servlet;

import com.foodordering.bean.*;
import com.foodordering.enums.*;
import com.foodordering.integration.AddressGeocoder;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticatedOrderPaymentServletTest {
    private final ProfileBean customer = new ProfileBean("KH01", AccountType.CUSTOMER, "Khách", "customer@example.com", "0901234567", null);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final CustomerOrderService orders = mock(CustomerOrderService.class);
    private final PayosPaymentService payos = mock(PayosPaymentService.class);
    private final CartService carts = mock(CartService.class);
    private final AccountService accounts = mock(AccountService.class);
    private final PromotionService promotions = mock(PromotionService.class);
    private final AddressGeocoder geocoder = mock(AddressGeocoder.class);
    AuthenticatedOrderPaymentServletTest() {
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/crave");
    }
    @Test void detailChecksOwnershipBeforePaymentLookup() throws Exception {
        when(request.getPathInfo()).thenReturn("/DH01");
        var order = new OrderBean(); order.setCustomerId("KH99");
        when(orders.getOrderById("DH01")).thenReturn(order);
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer);
            new OrderServlet(orders, payos).doGet(request, response);
        }
        verify(response).sendError(eq(403), anyString());
        verifyNoInteractions(payos);
    }
    @Test void listUsesSessionCustomerAndForwardsToJsp() throws Exception {
        var dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher("/WEB-INF/views/order/orders.jsp")).thenReturn(dispatcher);
        when(orders.getOrdersByCustomer("KH01")).thenReturn(List.of());
        when(request.getParameter("customerId")).thenReturn("KH99");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer);
            new OrderServlet(orders, payos).doGet(request, response);
        }
        verify(orders).getOrdersByCustomer("KH01"); verify(dispatcher).forward(request, response);
        verify(request, never()).getInputStream();
    }
    @Test void cancelReadsFormReasonAndRedirects() throws Exception {
        when(request.getPathInfo()).thenReturn("/DH01/cancel");
        when(request.getParameter("reason")).thenReturn("Đổi lịch");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer);
            new OrderServlet(orders, payos).doPost(request, response);
        }
        verify(orders).cancelOrder("DH01", "KH01", "Đổi lịch"); verify(payos).cancelForOrder("DH01");
        verify(response).sendRedirect("/crave/orders/DH01"); verify(request, never()).getInputStream();
    }
    @Test void checkoutBuildsFormAndIgnoresClientAmountsAndCustomerId() throws Exception {
        var created = new OrderBean(); created.setOrderId("DH01");
        when(orders.createOrder(eq("KH01"), any())).thenReturn(created);
        when(request.getParameter("action")).thenReturn("placeOrder"); when(request.getParameter("fulfillmentType")).thenReturn("PICKUP");
        when(request.getParameter("paymentMethod")).thenReturn("CASH"); when(request.getParameter("receiverName")).thenReturn("Khách");
        when(request.getParameter("receiverPhone")).thenReturn("0901234567"); when(request.getParameter("customerId")).thenReturn("KH99");
        when(request.getParameter("totalAmount")).thenReturn("1");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer);
            checkout().doPost(request, response);
        }
        var form = ArgumentCaptor.forClass(OrderForm.class); verify(orders).createOrder(eq("KH01"), form.capture());
        assertEquals(FulfillmentType.PICKUP, form.getValue().getFulfillmentType()); assertEquals(PaymentMethod.CASH, form.getValue().getPaymentMethod());
        assertEquals("Khách", form.getValue().getReceiverName()); verify(session).removeAttribute("appliedPromotionCode");
        verify(session).setAttribute("cartItemCount", 0);
        verify(response).sendRedirect("/crave/orders/DH01"); verify(request, never()).getInputStream();
    }
    @Test void deliveryQuoteUsesVerifiedSubtotalAndRendersJsp() throws Exception {
        var cart = new CartBean(); var dispatcher = mock(RequestDispatcher.class);
        when(carts.getOrCreateCart("KH01")).thenReturn(cart); when(carts.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("150001"));
        when(accounts.listAddresses("KH01")).thenReturn(List.of());
        when(request.getRequestDispatcher("/WEB-INF/views/order/checkout.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("quote");
        when(request.getParameter("deliveryLatitude")).thenReturn("10.91"); when(request.getParameter("deliveryLongitude")).thenReturn("106.77");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer); checkout().doPost(request, response);
        }
        verify(request).setAttribute("fee", BigDecimal.ZERO); verify(request).setAttribute("deliveryReady", true);
        verify(dispatcher).forward(request, response); verifyNoInteractions(orders);
    }
    @Test void pickupOrderIgnoresMalformedRetainedDeliveryCoordinates() throws Exception {
        var created = new OrderBean(); created.setOrderId("DH01");
        when(orders.createOrder(eq("KH01"), any())).thenReturn(created);
        when(request.getParameter("action")).thenReturn("placeOrder");
        when(request.getParameter("fulfillmentType")).thenReturn("PICKUP");
        when(request.getParameter("paymentMethod")).thenReturn("CASH");
        when(request.getParameter("deliveryLatitude")).thenReturn("invalid");
        when(request.getParameter("deliveryLongitude")).thenReturn("NaN");
        when(request.getParameter("deliveryAddress")).thenReturn("Old delivery address");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer);
            checkout().doPost(request, response);
        }
        var form = ArgumentCaptor.forClass(OrderForm.class);
        verify(orders).createOrder(eq("KH01"), form.capture());
        assertNull(form.getValue().getDeliveryLatitude());
        assertNull(form.getValue().getDeliveryLongitude());
        assertNull(form.getValue().getDeliveryAddress());
        verify(response).sendRedirect("/crave/orders/DH01");
    }
    @Test void pickupQuoteIgnoresMalformedRetainedDeliveryCoordinates() throws Exception {
        var dispatcher = mock(RequestDispatcher.class);
        when(carts.getOrCreateCart("KH01")).thenReturn(new CartBean());
        when(carts.getVerifiedSubtotal("KH01")).thenReturn(new BigDecimal("100000"));
        when(request.getRequestDispatcher("/WEB-INF/views/order/checkout.jsp")).thenReturn(dispatcher);
        when(request.getParameter("action")).thenReturn("quote");
        when(request.getParameter("fulfillmentType")).thenReturn("PICKUP");
        when(request.getParameter("deliveryLatitude")).thenReturn("invalid");
        when(request.getParameter("deliveryLongitude")).thenReturn("NaN");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer);
            checkout().doPost(request, response);
        }
        verify(request).setAttribute("fee", BigDecimal.ZERO);
        verify(request).setAttribute("deliveryReady", true);
        verify(request).setAttribute("total", new BigDecimal("100000"));
        verify(dispatcher).forward(request, response);
    }
    @Test void addressSearchPassesStoreFocusAndRendersCandidates() throws Exception {
        var results = List.of(new AddressGeocoder.Location(10.9, 106.7, "Địa chỉ", false));
        when(geocoder.search(eq("Địa chỉ"), any(), any())).thenReturn(results);
        when(request.getParameter("action")).thenReturn("search"); when(request.getParameter("deliveryAddress")).thenReturn("Địa chỉ");
        when(carts.getOrCreateCart("KH01")).thenReturn(new CartBean()); when(carts.getVerifiedSubtotal("KH01")).thenReturn(BigDecimal.ZERO);
        when(request.getRequestDispatcher("/WEB-INF/views/order/checkout.jsp")).thenReturn(mock(RequestDispatcher.class));
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireCustomer(request)).thenReturn(customer); checkout().doPost(request, response);
        }
        verify(request).setAttribute("locations", results); verifyNoInteractions(orders);
    }
    private CheckoutServlet checkout() { return new CheckoutServlet(carts, accounts, new DeliveryFeeService(), geocoder, promotions, orders); }
}
