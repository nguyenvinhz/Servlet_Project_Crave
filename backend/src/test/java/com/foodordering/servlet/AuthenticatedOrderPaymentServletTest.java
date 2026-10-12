package com.foodordering.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodordering.dto.OrderRequest;
import com.foodordering.dto.OrderResponse;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.ApiException;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.PaymentService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthenticatedOrderPaymentServletTest {
    private static final ProfileResponse CUSTOMER = new ProfileResponse("KH01", AccountType.CUSTOMER,
            "Customer", "customer@example.com", "0901234567", null);
    private static final String CHECKOUT = "{\"receiverName\":\"Customer\",\"receiverPhone\":\"0901234567\","
            + "\"fulfillmentType\":\"PICKUP\",\"paymentMethod\":\"CASH\"}";
    private static final String ORDER_STATUS = "{\"status\":\"PREPARING\",\"note\":\"Confirmed\"}";
    private static final String PAYMENT_STATUS = "{\"status\":\"SUCCESS\",\"transactionRef\":\"TX01\"}";

    private CustomerOrderService orders;
    private PaymentService payments;
    private OrderServlet customerServlet;
    private AdminOrderServlet adminServlet;
    private PaymentServlet paymentServlet;

    @BeforeEach
    void setUp() throws Exception {
        orders = mock(CustomerOrderService.class);
        payments = mock(PaymentService.class);
        customerServlet = new OrderServlet(orders);
        adminServlet = new AdminOrderServlet(orders);
        paymentServlet = new PaymentServlet(payments);
        customerServlet.init();
        adminServlet.init();
        paymentServlet.init();
    }

    @Test
    void orderAndPaymentRoutesRequirePrincipalBeforeReadingPayload() throws Exception {
        for (Route route : routes()) {
            Exchange exchange = request(route, null, "{invalid");
            when(exchange.request.getHeader("X-Customer-Id")).thenReturn("KH99");
            when(exchange.request.getHeader("X-Employee-Id")).thenReturn("NV99");
            when(exchange.request.getParameter("customerId")).thenReturn("KH99");
            when(exchange.request.getParameter("employeeId")).thenReturn("NV99");

            exchange.send(route.servlet);

            exchange.assertError(401, "UNAUTHORIZED");
            verify(exchange.request, never()).getInputStream();
            verify(exchange.request, never()).getSession(true);
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void legacySessionIdsCannotAuthenticateOrderOrPaymentRequests() throws Exception {
        for (Route route : routes()) {
            Exchange exchange = request(route, null, "{}");
            HttpSession legacy = mock(HttpSession.class);
            when(exchange.request.getSession(false)).thenReturn(legacy);
            when(legacy.getAttribute("customerId")).thenReturn("KH01");
            when(legacy.getAttribute("employeeId")).thenReturn("NV01");
            when(legacy.getAttribute("accountType")).thenReturn("EMPLOYEE");
            when(legacy.getAttribute("role")).thenReturn("ADMIN");

            exchange.send(route.servlet);

            exchange.assertError(401, "UNAUTHORIZED");
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void customerOrdersRejectEmployeePrincipals() throws Exception {
        for (String method : new String[]{"GET", "POST"}) {
            Route route = new Route(customerServlet, method, "/api/orders", null);
            Exchange exchange = request(route, employee(EmployeeRole.ADMIN), CHECKOUT);

            exchange.send(route.servlet);

            exchange.assertError(403, "FORBIDDEN");
        }
        verifyNoInteractions(orders);
    }

    @Test
    void orderListUsesCustomerPrincipalInsteadOfClientOrLegacyIds() throws Exception {
        when(orders.getOrdersByCustomer("KH01")).thenReturn(List.of());
        Route route = new Route(customerServlet, "GET", "/api/orders", null);
        Exchange exchange = request(route, CUSTOMER, "");
        spoofIdentity(exchange);

        exchange.send(customerServlet);

        exchange.assertStatus(200);
        assertTrue(exchange.json().path("success").asBoolean());
        verify(orders).getOrdersByCustomer("KH01");
        verify(orders, never()).getOrdersByCustomer("KH99");
    }

    @Test
    void checkoutUsesCustomerPrincipalInsteadOfClientOrLegacyIds() throws Exception {
        OrderResponse created = order("DH01", "KH01");
        when(orders.createOrder(eq("KH01"), any(OrderRequest.class))).thenReturn(created);
        Route route = new Route(customerServlet, "POST", "/api/orders", null);
        Exchange exchange = request(route, CUSTOMER, CHECKOUT);
        spoofIdentity(exchange);

        exchange.send(customerServlet);

        exchange.assertStatus(201);
        assertEquals("DH01", exchange.json().path("data").path("orderId").asText());
        verify(orders).createOrder(eq("KH01"), any(OrderRequest.class));
        verify(orders, never()).createOrder(eq("KH99"), any());
    }

    @Test
    void checkoutReturnsDomainBadRequestsWithTheirOriginalErrorCodes() throws Exception {
        for (ApiException failure : List.of(new BadRequestException(ErrorCode.CART_EMPTY),
                new BadRequestException(ErrorCode.INVALID_OPTION),
                new PromotionValidationException(ErrorCode.PROMOTION_EXPIRED))) {
            doThrow(failure).when(orders).createOrder(eq("KH01"), any(OrderRequest.class));
            Route route = new Route(customerServlet, "POST", "/api/orders", null);
            Exchange exchange = request(route, CUSTOMER, CHECKOUT);

            exchange.send(customerServlet);

            exchange.assertError(400, failure.getErrorCode().getCode());
        }
    }

    @Test
    void checkoutRejectsCustomerIdentityInJsonPayload() throws Exception {
        Route route = new Route(customerServlet, "POST", "/api/orders", null);
        Exchange exchange = request(route, CUSTOMER, "{\"customerId\":\"KH99\"}");

        exchange.send(customerServlet);

        exchange.assertError(400, "INVALID_REQUEST");
        verifyNoInteractions(orders);
    }

    @Test
    void orderDetailRequiresOwnershipAndDoesNotExposeForeignOrderData() throws Exception {
        for (String owner : new String[]{"KH02", null}) {
            OrderResponse foreign = order("DH99", owner);
            foreign.setDeliveryAddress("private address");
            when(orders.getOrderById("DH99")).thenReturn(foreign);
            Route route = new Route(customerServlet, "GET", "/api/orders", "/DH99");
            Exchange exchange = request(route, CUSTOMER, "");
            spoofIdentity(exchange);

            exchange.send(customerServlet);

            exchange.assertError(403, "FORBIDDEN");
            assertTrue(exchange.json().path("data").isMissingNode() || exchange.json().path("data").isNull());
            assertFalse(exchange.body.toString().contains("private address"));
        }
    }

    @Test
    void ownerCanViewOwnOrderDetail() throws Exception {
        when(orders.getOrderById("DH01")).thenReturn(order("DH01", "KH01"));
        Route route = new Route(customerServlet, "GET", "/api/orders", "/DH01");
        Exchange exchange = request(route, CUSTOMER, "");

        exchange.send(customerServlet);

        exchange.assertStatus(200);
        assertEquals("KH01", exchange.json().path("data").path("customerId").asText());
    }

    @Test
    void orderAndPaymentAdminRoutesRejectCustomersAndUnrelatedEmployeeRoles() throws Exception {
        for (ProfileResponse user : new ProfileResponse[]{CUSTOMER, employee(EmployeeRole.MENU_MANAGER),
                employee(EmployeeRole.PROMOTION_MANAGER), employee(EmployeeRole.HR_MANAGER), employee(null)}) {
            for (Route route : adminRoutes()) {
                Exchange exchange = request(route, user, "{invalid");

                exchange.send(route.servlet);

                exchange.assertError(403, "FORBIDDEN");
                verify(exchange.request, never()).getInputStream();
            }
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void orderStaffAndAdminsCanReadOrdersAndRecordTheirAuthenticatedEmployeeId() throws Exception {
        when(orders.getAllOrdersForAdmin()).thenReturn(List.of());
        for (EmployeeRole role : new EmployeeRole[]{EmployeeRole.ORDER_STAFF, EmployeeRole.ADMIN}) {
            ProfileResponse user = employee(role);
            Exchange list = request(new Route(adminServlet, "GET", "/api/admin/orders", null), user, "");
            list.send(adminServlet);
            list.assertStatus(200);

            Exchange update = request(new Route(adminServlet, "PATCH", "/api/admin/orders", "/DH01/status"),
                    user, ORDER_STATUS);
            spoofIdentity(update);
            update.send(adminServlet);
            update.assertStatus(200);

            Exchange payment = request(new Route(paymentServlet, "PATCH", "/api/admin/payments", "/TT01/status"),
                    user, PAYMENT_STATUS);
            spoofIdentity(payment);
            payment.send(paymentServlet);
            payment.assertStatus(200);
        }
        verify(orders, times(2)).updateOrderStatus("DH01", OrderStatus.PREPARING, "NV01", "Confirmed");
        verify(orders, never()).updateOrderStatus(anyString(), any(), eq("NV99"), any());
        verify(payments, times(2)).updatePaymentStatus("TT01", PaymentStatus.SUCCESS, "TX01");
    }

    @Test
    void adminStatusMutationsRejectEmployeeIdentityInJsonPayload() throws Exception {
        for (Route route : adminRoutes()) {
            if (!"PATCH".equals(route.method)) {
                continue;
            }
            Exchange exchange = request(route, employee(EmployeeRole.ORDER_STAFF),
                    "{\"employeeId\":\"NV99\",\"status\":\"SUCCESS\"}");

            exchange.send(route.servlet);

            exchange.assertError(400, "INVALID_REQUEST");
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void nullOrMalformedCheckoutBodyReturns400WithoutCreatingOrder() throws Exception {
        for (String payload : new String[]{"null", "{invalid", ""}) {
            Exchange exchange = request(new Route(customerServlet, "POST", "/api/orders", null), CUSTOMER, payload);

            exchange.send(customerServlet);

            exchange.assertError(400, "INVALID_REQUEST");
        }
        verifyNoInteractions(orders);
    }

    @Test
    void nullMissingOrInvalidAdminStatusReturns400BeforeCallingServices() throws Exception {
        for (Route route : adminRoutes()) {
            if (!"PATCH".equals(route.method)) {
                continue;
            }
            for (String payload : new String[]{"null", "{}", "{\"status\":null}", "{\"status\":\"\"}",
                    "{\"status\":\"NO_SUCH_STATUS\"}", "{invalid", ""}) {
                Exchange exchange = request(route, employee(EmployeeRole.ORDER_STAFF), payload);

                exchange.send(route.servlet);

                exchange.assertError(400, "INVALID_REQUEST");
            }
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void malformedAdminStatusPathsCannotTargetAnUnintendedResource() throws Exception {
        for (HttpServlet servlet : new HttpServlet[]{adminServlet, paymentServlet}) {
            for (String path : new String[]{"/status", "//status", "/DH01/extra/status", "/DH01//status", "/DH01"}) {
                Exchange exchange = request(new Route(servlet, "PATCH", "/api/admin/orders", path),
                        employee(EmployeeRole.ORDER_STAFF), ORDER_STATUS);

                exchange.send(servlet);

                exchange.assertError(400, "INVALID_REQUEST");
                verify(exchange.request, never()).getInputStream();
            }
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void changedLoginAfterFilterAuthorizationRejectsEveryOrderAndPaymentOperation() throws Exception {
        for (Route route : routes()) {
            ProfileResponse original = route.servlet == customerServlet ? CUSTOMER : employee(EmployeeRole.ORDER_STAFF);
            Exchange exchange = request(route, original, "{}");
            SessionAuth.capture(exchange.request);
            // Even a repeat login as the same account is a different authentication revision.
            SessionAuth.store(exchange.session, original);

            exchange.send(route.servlet);

            exchange.assertError(401, "UNAUTHORIZED");
            verify(exchange.session, never()).invalidate();
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void logoutAfterFilterAuthorizationRejectsEveryOrderAndPaymentOperation() throws Exception {
        for (Route route : routes()) {
            ProfileResponse user = route.servlet == customerServlet ? CUSTOMER : employee(EmployeeRole.ORDER_STAFF);
            Exchange exchange = request(route, user, "{}");
            SessionAuth.capture(exchange.request);
            when(exchange.request.getSession(false)).thenReturn(null);

            exchange.send(route.servlet);

            exchange.assertError(401, "UNAUTHORIZED");
            verify(exchange.request, never()).getSession(true);
        }
        verifyNoInteractions(orders, payments);
    }

    @Test
    void serviceFailuresUseSanitizedSharedJsonEnvelope() throws Exception {
        when(orders.getOrdersByCustomer("KH01")).thenThrow(new IllegalStateException("jdbc private-host secret"));
        Exchange customer = request(new Route(customerServlet, "GET", "/api/orders", null), CUSTOMER, "");
        customer.send(customerServlet);
        customer.assertError(500, "INTERNAL_SERVER_ERROR");
        assertFalse(customer.body.toString().contains("private-host"));
        assertFalse(customer.body.toString().contains("secret"));

        doThrow(new IllegalStateException("jdbc private-host secret")).when(payments)
                .updatePaymentStatus("TT01", PaymentStatus.SUCCESS, "TX01");
        Exchange payment = request(new Route(paymentServlet, "PATCH", "/api/admin/payments", "/TT01/status"),
                employee(EmployeeRole.ORDER_STAFF), PAYMENT_STATUS);
        payment.send(paymentServlet);
        payment.assertError(500, "INTERNAL_SERVER_ERROR");
        assertFalse(payment.body.toString().contains("secret"));
    }

    private List<Route> adminRoutes() {
        return List.of(new Route(adminServlet, "GET", "/api/admin/orders", null),
                new Route(adminServlet, "PATCH", "/api/admin/orders", "/DH01/status"),
                new Route(paymentServlet, "PATCH", "/api/admin/payments", "/TT01/status"));
    }

    private List<Route> routes() {
        return List.of(new Route(customerServlet, "GET", "/api/orders", null),
                new Route(customerServlet, "POST", "/api/orders", null),
                adminRoutes().get(0), adminRoutes().get(1), adminRoutes().get(2));
    }

    private static ProfileResponse employee(EmployeeRole role) {
        return new ProfileResponse("NV01", AccountType.EMPLOYEE, "Employee", "employee@example.com", "0901234568", role);
    }

    private static OrderResponse order(String id, String customerId) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(id);
        response.setCustomerId(customerId);
        return response;
    }

    private void spoofIdentity(Exchange exchange) {
        when(exchange.request.getHeader("X-Customer-Id")).thenReturn("KH99");
        when(exchange.request.getHeader("X-Employee-Id")).thenReturn("NV99");
        when(exchange.request.getParameter("customerId")).thenReturn("KH99");
        when(exchange.request.getParameter("employeeId")).thenReturn("NV99");
        when(exchange.session.getAttribute("customerId")).thenReturn("KH99");
        when(exchange.session.getAttribute("employeeId")).thenReturn("NV99");
    }

    private Exchange request(Route route, ProfileResponse principal, String payload) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = principal == null ? null : mock(HttpSession.class);
        StringWriter body = new StringWriter();
        when(request.getMethod()).thenReturn(route.method);
        when(request.getServletPath()).thenReturn(route.path);
        when(request.getPathInfo()).thenReturn(route.pathInfo);
        when(request.getProtocol()).thenReturn("HTTP/1.1");
        when(request.getSession(false)).thenReturn(session);
        ByteArrayInputStream bytes = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
            @Override public int read() { return bytes.read(); }
        });
        if (session != null) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(SessionAuth.CURRENT_USER, principal);
            when(session.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
            doAnswer(call -> {
                attributes.put(call.getArgument(0), call.getArgument(1));
                return null;
            }).when(session).setAttribute(anyString(), any());
        }
        Map<String, Object> attributes = new HashMap<>();
        when(request.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> {
            attributes.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(request).setAttribute(anyString(), any());
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return new Exchange(request, response, session, body);
    }

    private record Route(HttpServlet servlet, String method, String path, String pathInfo) {
    }

    private record Exchange(HttpServletRequest request, HttpServletResponse response, HttpSession session,
                            StringWriter body) {
        void send(HttpServlet servlet) throws Exception {
            servlet.service((ServletRequest) request, (ServletResponse) response);
        }

        void assertStatus(int status) {
            verify(response).setStatus(status);
        }

        JsonNode json() throws Exception {
            return JsonProvider.objectMapper().readTree(body.toString());
        }

        void assertError(int status, String code) throws Exception {
            assertStatus(status);
            assertFalse(json().path("success").asBoolean());
            assertEquals(code, json().path("error").path("code").asText());
            verify(response).setContentType("application/json");
        }
    }
}
