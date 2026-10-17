package vn.edu.crave.controller.payment;

import vn.edu.crave.controller.BaseServlet;
import vn.edu.crave.controller.HealthServlet;
import vn.edu.crave.controller.account.*;
import vn.edu.crave.controller.menu.*;
import vn.edu.crave.controller.cart.*;
import vn.edu.crave.controller.order.*;
import vn.edu.crave.controller.payment.*;
import vn.edu.crave.controller.admin.*;


import vn.edu.crave.model.form.OrderRequest;
import vn.edu.crave.model.viewmodel.OrderResponse;
import vn.edu.crave.model.viewmodel.OrderSummaryResponse;
import vn.edu.crave.model.viewmodel.ProfileResponse;
import vn.edu.crave.model.enums.*;
import vn.edu.crave.exception.ApiException;
import vn.edu.crave.exception.BadRequestException;
import vn.edu.crave.exception.PromotionValidationException;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.CustomerOrderService;
import vn.edu.crave.model.service.PaymentService;
import vn.edu.crave.model.service.PayosPaymentService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Session authorization remains required after moving HTTP payloads to native forms and JSP. */
class AuthenticatedOrderPaymentServletTest {
    private static final ProfileResponse CUSTOMER = new ProfileResponse("KH01", AccountType.CUSTOMER,
            "Customer", "customer@example.com", "0901234567", null);
    private CustomerOrderService orders;
    private PaymentService payments;
    private PayosPaymentService payos;
    private OrderServlet customerServlet;
    private OrdersPageServlet pageServlet;
    private AdminOrderServlet adminServlet;
    private PaymentServlet paymentServlet;

    @BeforeEach void setUp() throws Exception {
        orders = mock(CustomerOrderService.class);
        payments = mock(PaymentService.class);
        payos = mock(PayosPaymentService.class);
        customerServlet = new OrderServlet(orders);
        pageServlet = new OrdersPageServlet(orders, payos);
        adminServlet = new AdminOrderServlet(orders);
        paymentServlet = new PaymentServlet(payments);
        for (HttpServlet servlet : new HttpServlet[]{customerServlet, pageServlet, adminServlet, paymentServlet}) {
            ServletConfig config = mock(ServletConfig.class);
            when(config.getServletContext()).thenReturn(mock(ServletContext.class));
            servlet.init(config);
        }
        var field = OrderServlet.class.getDeclaredField("payos");
        field.setAccessible(true);
        field.set(customerServlet, payos);
    }

    @Test void mutationsRequirePrincipalBeforeReadingFormOrCreatingSession() throws Exception {
        for (HttpServlet servlet : mutations()) {
            Exchange exchange = exchange(null);
            spoofIdentity(exchange);
            exchange.send(servlet);
            verify(exchange.response).sendError(eq(401), anyString());
            verify(exchange.request, never()).getInputStream();
            verify(exchange.request, never()).getSession();
            verify(exchange.request, never()).getSession(true);
        }
        verifyNoInteractions(orders, payments, payos);
    }

    @Test void legacySessionIdsCannotAuthenticateOrderOrPaymentForms() throws Exception {
        for (HttpServlet servlet : mutations()) {
            Exchange exchange = exchange(null);
            exchange.sessionValues.putAll(Map.of("customerId", "KH01", "employeeId", "NV01",
                    "accountType", "EMPLOYEE", "role", "ADMIN"));
            exchange.send(servlet);
            verify(exchange.response).sendError(eq(401), anyString());
        }
        verifyNoInteractions(orders, payments);
    }

    @Test void customerPageRequiresCustomerPrincipalBeforeLoadingOrders() throws Exception {
        for (ProfileResponse principal : new ProfileResponse[]{null, employee(EmployeeRole.ADMIN)}) {
            Exchange exchange = exchange(principal);
            pageServlet.doGet(exchange.request, exchange.response);
            verify(exchange.response).sendError(eq(principal == null ? 401 : 403), anyString());
            verifyNoInteractions(exchange.dispatcher);
        }
        verifyNoInteractions(orders, payos);
    }

    @Test void employeeCannotPlaceCustomerOrder() throws Exception {
        Exchange exchange = exchange(employee(EmployeeRole.ADMIN));
        checkout(exchange);
        exchange.send(customerServlet);
        verify(exchange.response).sendError(eq(403), anyString());
        verifyNoInteractions(orders, payos);
    }

    @Test void orderListUsesSessionCustomerAndForwardsDataToJsp() throws Exception {
        OrderSummaryResponse summary = new OrderSummaryResponse();
        summary.setOrderId("DH01");
        List<OrderSummaryResponse> result = List.of(summary);
        when(orders.getOrdersByCustomer("KH01")).thenReturn(result);
        Exchange exchange = exchange(CUSTOMER);
        spoofIdentity(exchange);
        pageServlet.doGet(exchange.request, exchange.response);
        verify(orders).getOrdersByCustomer("KH01");
        verify(orders, never()).getOrdersByCustomer("KH99");
        assertSame(result, exchange.attributes.get("orders"));
        verify(exchange.request).getRequestDispatcher("/WEB-INF/views/order/orders.jsp");
        verify(exchange.dispatcher).forward(exchange.request, exchange.response);
    }

    @Test void checkoutBindsFormAndPromotionFromAuthenticatedSession() throws Exception {
        when(orders.createOrder(eq("KH01"), any())).thenReturn(order("DH01", "KH01"));
        Exchange exchange = exchange(CUSTOMER);
        checkout(exchange);
        spoofIdentity(exchange);
        exchange.parameters.put("promotionId", "FORGED");
        exchange.sessionValues.put("selectedPromotionId", "PROMO-SESSION");
        exchange.sessionValues.put("selectedPromotionCode", "SAVE10");
        exchange.send(customerServlet);
        ArgumentCaptor<OrderRequest> form = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orders).createOrder(eq("KH01"), form.capture());
        assertEquals("Customer", form.getValue().getReceiverName());
        assertEquals("0901234567", form.getValue().getReceiverPhone());
        assertEquals(FulfillmentType.PICKUP, form.getValue().getFulfillmentType());
        assertEquals(PaymentMethod.CASH, form.getValue().getPaymentMethod());
        assertEquals("PROMO-SESSION", form.getValue().getPromotionId());
        assertFalse(exchange.sessionValues.containsKey("selectedPromotionId"));
        assertFalse(exchange.sessionValues.containsKey("selectedPromotionCode"));
        assertEquals(0, exchange.sessionValues.get("cartItemCount"));
        verify(exchange.response).sendRedirect("/crave/orders/DH01");
        verify(exchange.request, never()).getInputStream();
        verifyNoInteractions(payos);
    }

    @Test void deliveryCoordinatesAndOnlinePaymentUseServerCreatedOrder() throws Exception {
        when(orders.createOrder(eq("KH01"), any())).thenReturn(order("DH01", "KH01"));
        Exchange exchange = exchange(CUSTOMER);
        checkout(exchange);
        exchange.parameters.putAll(Map.of("fulfillmentType", "DELIVERY", "paymentMethod", "BANK_TRANSFER",
                "deliveryLatitude", "10.775", "deliveryLongitude", "106.701", "deliveryAddress", "12 Main Street"));
        exchange.send(customerServlet);
        ArgumentCaptor<OrderRequest> form = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orders).createOrder(eq("KH01"), form.capture());
        assertEquals(10.775, form.getValue().getDeliveryLatitude());
        assertEquals(106.701, form.getValue().getDeliveryLongitude());
        assertEquals("12 Main Street", form.getValue().getDeliveryAddress());
        verify(payos).create("DH01", "KH01");
        verify(exchange.response).sendRedirect("/crave/orders/DH01");
    }

    @Test void domainCheckoutErrorsKeepPromotionAndDisplayOriginalMessage() throws Exception {
        for (ApiException failure : List.of(new BadRequestException(ErrorCode.CART_EMPTY),
                new BadRequestException(ErrorCode.INVALID_OPTION),
                new PromotionValidationException(ErrorCode.PROMOTION_EXPIRED))) {
            doThrow(failure).when(orders).createOrder(eq("KH01"), any());
            Exchange exchange = exchange(CUSTOMER);
            checkout(exchange);
            exchange.sessionValues.put("selectedPromotionId", "PROMO-SESSION");
            exchange.send(customerServlet);
            assertEquals(failure.getMessage(), exchange.sessionValues.get("orderError"));
            assertEquals("PROMO-SESSION", exchange.sessionValues.get("selectedPromotionId"));
            verify(exchange.response).sendRedirect("/crave/checkout");
        }
    }

    @Test void missingInvalidEnumsOrCoordinatesCannotCreateOrder() throws Exception {
        List<Map<String, String>> invalidForms = List.of(Map.of(), Map.of("fulfillmentType", "INVALID"),
                Map.of("paymentMethod", "INVALID"),
                Map.of("fulfillmentType", "DELIVERY", "deliveryLatitude", "not-a-number"));
        for (Map<String, String> invalid : invalidForms) {
            Exchange exchange = exchange(CUSTOMER);
            if (!invalid.isEmpty()) checkout(exchange);
            exchange.parameters.putAll(invalid);
            exchange.send(customerServlet);
            assertNotNull(exchange.sessionValues.get("orderError"));
            verify(exchange.response).sendRedirect("/crave/checkout");
        }
        verifyNoInteractions(orders, payos);
    }

    @Test void foreignOrUnownedOrderIsNeverExposedToJsp() throws Exception {
        for (String owner : new String[]{"KH02", null}) {
            OrderResponse foreign = order("DH99", owner);
            foreign.setDeliveryAddress("private address");
            when(orders.getOrderById("DH99")).thenReturn(foreign);
            Exchange exchange = exchange(CUSTOMER);
            when(exchange.request.getPathInfo()).thenReturn("/DH99");
            spoofIdentity(exchange);
            pageServlet.doGet(exchange.request, exchange.response);
            verify(exchange.response).sendError(eq(403), anyString());
            assertFalse(exchange.attributes.containsKey("order"));
            verifyNoInteractions(exchange.dispatcher, payos);
        }
    }

    @Test void ownerCanViewOrderDetail() throws Exception {
        OrderResponse order = order("DH01", "KH01");
        when(orders.getOrderById("DH01")).thenReturn(order);
        Exchange exchange = exchange(CUSTOMER);
        when(exchange.request.getPathInfo()).thenReturn("/DH01");
        pageServlet.doGet(exchange.request, exchange.response);
        assertSame(order, exchange.attributes.get("order"));
        assertEquals(false, exchange.attributes.get("onlinePayment"));
        verify(exchange.request).getRequestDispatcher("/WEB-INF/views/order/order-detail.jsp");
        verify(exchange.dispatcher).forward(exchange.request, exchange.response);
    }

    @Test void cancellationUsesSessionOwnerAndSynchronizesGatewayAfterService() throws Exception {
        Exchange exchange = exchange(CUSTOMER);
        when(exchange.request.getServletPath()).thenReturn("/orders/cancel");
        exchange.parameters.putAll(Map.of("orderId", "DH01", "reason", "Changed plans"));
        spoofIdentity(exchange);
        exchange.send(customerServlet);
        var calls = inOrder(orders, payos);
        calls.verify(orders).cancelOrder("DH01", "KH01", "Changed plans");
        calls.verify(payos).cancelForOrder("DH01");
        verify(exchange.response).sendRedirect("/crave/orders/DH01");
    }

    @Test void unrelatedRolesCannotMutateOrderOrPayment() throws Exception {
        for (ProfileResponse user : new ProfileResponse[]{CUSTOMER, employee(EmployeeRole.MENU_MANAGER),
                employee(EmployeeRole.PROMOTION_MANAGER), employee(EmployeeRole.HR_MANAGER), employee(null)}) {
            for (HttpServlet servlet : new HttpServlet[]{adminServlet, paymentServlet}) {
                Exchange exchange = exchange(user);
                exchange.send(servlet);
                verify(exchange.response).sendError(eq(403), anyString());
                verify(exchange.request, never()).getSession();
            }
        }
        verifyNoInteractions(orders, payments);
    }

    @Test void authorizedStaffUseSessionEmployeeForStatusChanges() throws Exception {
        for (EmployeeRole role : new EmployeeRole[]{EmployeeRole.ORDER_STAFF, EmployeeRole.ADMIN}) {
            Exchange update = exchange(employee(role));
            spoofIdentity(update);
            update.parameters.putAll(Map.of("orderId", "DH01", "status", "PREPARING", "note", "Confirmed"));
            update.send(adminServlet);
            assertNotNull(update.sessionValues.get("adminOrderMessage"));
            Exchange payment = exchange(employee(role));
            spoofIdentity(payment);
            payment.parameters.putAll(Map.of("orderId", "DH01", "paymentId", "TT01", "status", "SUCCESS", "transactionRef", "TX01"));
            payment.send(paymentServlet);
            assertNotNull(payment.sessionValues.get("adminOrderMessage"));
        }
        verify(orders, times(2)).updateOrderStatus("DH01", OrderStatus.PREPARING, "NV01", "Confirmed");
        verify(orders, never()).updateOrderStatus(anyString(), any(), eq("NV99"), any());
        verify(payments, times(2)).updatePaymentStatus("TT01", PaymentStatus.SUCCESS, "TX01");
    }

    @Test void missingOrInvalidStatusCannotCallAdminServices() throws Exception {
        for (HttpServlet servlet : new HttpServlet[]{adminServlet, paymentServlet}) {
            for (String status : new String[]{null, "", "NO_SUCH_STATUS"}) {
                Exchange exchange = exchange(employee(EmployeeRole.ORDER_STAFF));
                exchange.parameters.put("status", status);
                exchange.send(servlet);
                assertNotNull(exchange.sessionValues.get("adminOrderError"));
            }
        }
        verifyNoInteractions(orders, payments);
    }

    @Test void changedLoginAfterFilterAuthorizationRejectsEveryMutation() throws Exception {
        for (HttpServlet servlet : mutations()) {
            ProfileResponse user = servlet == customerServlet ? CUSTOMER : employee(EmployeeRole.ORDER_STAFF);
            Exchange exchange = exchange(user);
            SessionAuth.capture(exchange.request);
            SessionAuth.store(exchange.session, user);
            exchange.send(servlet);
            verify(exchange.response).sendError(eq(401), anyString());
            verify(exchange.session, never()).invalidate();
        }
        verifyNoInteractions(orders, payments, payos);
    }

    @Test void logoutAfterFilterAuthorizationRejectsEveryMutation() throws Exception {
        for (HttpServlet servlet : mutations()) {
            Exchange exchange = exchange(servlet == customerServlet ? CUSTOMER : employee(EmployeeRole.ORDER_STAFF));
            SessionAuth.capture(exchange.request);
            when(exchange.request.getSession(false)).thenReturn(null);
            exchange.send(servlet);
            verify(exchange.response).sendError(eq(401), anyString());
            verify(exchange.request, never()).getSession();
        }
        verifyNoInteractions(orders, payments, payos);
    }

    @Test void unexpectedFailuresDoNotExposeDatabaseDetails() throws Exception {
        when(orders.getOrdersByCustomer("KH01")).thenThrow(new IllegalStateException("jdbc private-host secret"));
        Exchange page = exchange(CUSTOMER);
        pageServlet.doGet(page.request, page.response);
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(page.response).sendError(eq(500), error.capture());
        assertFalse(error.getValue().contains("secret"));
        assertFalse(error.getValue().contains("private-host"));
        verifyNoInteractions(page.dispatcher);
        doThrow(new IllegalStateException("jdbc private-host secret")).when(payments)
                .updatePaymentStatus("TT01", PaymentStatus.SUCCESS, "TX01");
        Exchange form = exchange(employee(EmployeeRole.ORDER_STAFF));
        form.parameters.putAll(Map.of("paymentId", "TT01", "status", "SUCCESS", "transactionRef", "TX01"));
        form.send(paymentServlet);
        String message = (String) form.sessionValues.get("adminOrderError");
        assertNotNull(message);
        assertFalse(message.contains("secret"));
        assertFalse(message.contains("private-host"));
    }

    @Test void onlineOrderGatewayFailureKeepsDetailPageWithoutInternalErrorDetails() throws Exception {
        OrderResponse order = order("DH01", "KH01");
        OrderResponse.PaymentSummary payment = new OrderResponse.PaymentSummary();
        payment.setMethod("BANK_TRANSFER");
        payment.setStatus("PENDING");
        order.setPayment(payment);
        when(orders.getOrderById("DH01")).thenReturn(order);
        when(payos.status("DH01", "KH01")).thenThrow(new IllegalStateException("jdbc private-host password=secret"));
        Exchange exchange = exchange(CUSTOMER);
        when(exchange.request.getPathInfo()).thenReturn("/DH01");

        pageServlet.doGet(exchange.request, exchange.response);

        String message = (String) exchange.attributes.get("payosError");
        assertNotNull(message);
        assertFalse(message.isBlank());
        assertFalse(message.contains("secret"));
        assertFalse(message.contains("private-host"));
        assertSame(order, exchange.attributes.get("order"));
        assertEquals(true, exchange.attributes.get("onlinePayment"));
        verify(exchange.dispatcher).forward(exchange.request, exchange.response);
    }

    private List<HttpServlet> mutations() { return List.of(customerServlet, adminServlet, paymentServlet); }
    private static ProfileResponse employee(EmployeeRole role) {
        return new ProfileResponse("NV01", AccountType.EMPLOYEE, "Employee", "employee@example.com", "0901234568", role);
    }
    private static OrderResponse order(String id, String customerId) {
        OrderResponse order = new OrderResponse(); order.setOrderId(id); order.setCustomerId(customerId); return order;
    }
    private void checkout(Exchange exchange) {
        exchange.parameters.putAll(Map.of("fulfillmentType", "PICKUP", "receiverName", " Customer ",
                "receiverPhone", "0901234567", "paymentMethod", "CASH"));
    }
    private void spoofIdentity(Exchange exchange) {
        when(exchange.request.getHeader("X-Customer-Id")).thenReturn("KH99");
        when(exchange.request.getHeader("X-Employee-Id")).thenReturn("NV99");
        exchange.parameters.putAll(Map.of("customerId", "KH99", "employeeId", "NV99"));
        exchange.sessionValues.putAll(Map.of("customerId", "KH99", "employeeId", "NV99"));
    }
    private Exchange exchange(ProfileResponse principal) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        Map<String, Object> attributes = new HashMap<>(), sessionValues = new HashMap<>();
        Map<String, String> parameters = new HashMap<>();
        if (principal != null) sessionValues.put(SessionAuth.CURRENT_USER, principal);
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getServletPath()).thenReturn("/orders/create");
        when(request.getParameter(anyString())).thenAnswer(call -> parameters.get(call.getArgument(0)));
        when(request.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> { attributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(request).setAttribute(anyString(), any());
        when(session.getAttribute(anyString())).thenAnswer(call -> sessionValues.get(call.getArgument(0)));
        doAnswer(call -> { sessionValues.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
        doAnswer(call -> { sessionValues.remove(call.getArgument(0)); return null; })
                .when(session).removeAttribute(anyString());
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        return new Exchange(request, response, session, dispatcher, attributes, sessionValues, parameters);
    }
    private record Exchange(HttpServletRequest request, HttpServletResponse response, HttpSession session,
                            RequestDispatcher dispatcher, Map<String, Object> attributes,
                            Map<String, Object> sessionValues, Map<String, String> parameters) {
        void send(HttpServlet servlet) throws Exception {
            if (servlet instanceof OrderServlet order) order.doPost(request, response);
            else if (servlet instanceof AdminOrderServlet admin) admin.doPost(request, response);
            else ((PaymentServlet) servlet).doPost(request, response);
        }
    }
}
