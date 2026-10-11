package com.foodordering.api;

import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartApiServletTest {

    private StubCartService cartService;
    private CartApiServlet servlet;

    static class StubCartService extends CartService {
        String lastMethod;
        String lastCustomerId;
        AddToCartRequest lastAddReq;
        UpdateCartItemRequest lastUpdateReq;
        String lastCartItemId;
        RuntimeException exceptionToThrow;

        @Override
        public CartDto getOrCreateCart(String customerId) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.lastMethod = "getOrCreateCart";
            this.lastCustomerId = customerId;
            return new CartDto("GH01", customerId);
        }

        @Override
        public CartDto addItem(String customerId, AddToCartRequest request) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.lastMethod = "addItem";
            this.lastCustomerId = customerId;
            this.lastAddReq = request;
            return new CartDto("GH01", customerId);
        }

        @Override
        public CartDto updateItem(String customerId, UpdateCartItemRequest request) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.lastMethod = "updateItem";
            this.lastCustomerId = customerId;
            this.lastUpdateReq = request;
            return new CartDto("GH01", customerId);
        }

        @Override
        public CartDto removeItem(String customerId, String cartItemId) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.lastMethod = "removeItem";
            this.lastCustomerId = customerId;
            this.lastCartItemId = cartItemId;
            return new CartDto("GH01", customerId);
        }

        @Override
        public CartDto clearCart(String customerId) {
            if (exceptionToThrow != null) throw exceptionToThrow;
            this.lastMethod = "clearCart";
            this.lastCustomerId = customerId;
            return new CartDto("GH01", customerId);
        }
    }

    @BeforeEach
    void setUp() {
        cartService = new StubCartService();
        servlet = new CartApiServlet(cartService);
    }

    private void invokeServletMethod(String methodName, HttpServletRequest req, HttpServletResponse resp) throws Exception {
        Method method = CartApiServlet.class.getDeclaredMethod(methodName, HttpServletRequest.class, HttpServletResponse.class);
        method.setAccessible(true);
        method.invoke(servlet, req, resp);
    }

    private HttpServletRequest mockRequest(String pathInfo, String customerId, String body) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getPathInfo()).thenReturn(pathInfo);

        if (customerId != null) {
            HttpSession session = mock(HttpSession.class);
            when(req.getSession(false)).thenReturn(session);
            Map<String, Object> sessionAttributes = new HashMap<>();
            sessionAttributes.put("currentUser", new ProfileResponse(customerId,
                    AccountType.CUSTOMER, "Customer", "customer@example.com", "0901234567", null));
            when(session.getAttribute(any(String.class)))
                    .thenAnswer(call -> sessionAttributes.get(call.getArgument(0)));
            doAnswer(call -> {
                sessionAttributes.put(call.getArgument(0), call.getArgument(1));
                return null;
            }).when(session).setAttribute(any(String.class), any());
            doAnswer(call -> {
                sessionAttributes.remove(call.getArgument(0));
                return null;
            }).when(session).removeAttribute(any(String.class));
        } else {
            when(req.getSession(false)).thenReturn(null);
            when(req.getHeader("X-Customer-Id")).thenReturn(null);
            when(req.getParameter("customerId")).thenReturn(null);
        }

        Map<String, Object> requestAttributes = new HashMap<>();
        when(req.getAttribute(any(String.class)))
                .thenAnswer(call -> requestAttributes.get(call.getArgument(0)));
        doAnswer(call -> {
            requestAttributes.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(req).setAttribute(any(String.class), any());

        if (body != null) {
            when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body)));
        } else {
            when(req.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        }

        return req;
    }

    @Test
    @DisplayName("Chưa đăng nhập (thiếu customerId) trả về HTTP 401 UNAUTHORIZED")
    void testUnauthorizedReturns401() throws Exception {
        HttpServletRequest req = mockRequest(null, null, null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doGet", req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String responseJson = sw.toString();
        assertTrue(responseJson.contains("\"success\":false"));
        assertTrue(responseJson.contains("UNAUTHORIZED"));
    }

    @Test
    void requestAndLegacySessionIdsCannotAuthenticateAnyCartOperation() throws Exception {
        for (String method : new String[]{"doGet", "doPost", "doPut", "doDelete"}) {
            HttpServletRequest req = mockRequest(null, null, null);
            HttpSession legacySession = mock(HttpSession.class);
            when(req.getSession(false)).thenReturn(legacySession);
            when(legacySession.getAttribute("customerId")).thenReturn("KH99");
            when(req.getHeader("X-Customer-Id")).thenReturn("KH99");
            when(req.getParameter("customerId")).thenReturn("KH99");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(body));

            invokeServletMethod(method, req, resp);

            verify(resp).setStatus(401);
            assertTrue(body.toString().contains("UNAUTHORIZED"));
            assertNull(cartService.lastMethod);
        }
    }

    @Test
    void cartUsesAuthenticatedPrincipalWhenOtherCustomerIdsDisagree() throws Exception {
        HttpServletRequest req = mockRequest(null, "KH01", null);
        when(req.getSession(false).getAttribute("customerId")).thenReturn("KH99");
        when(req.getHeader("X-Customer-Id")).thenReturn("KH99");
        when(req.getParameter("customerId")).thenReturn("KH99");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        invokeServletMethod("doGet", req, resp);

        assertEquals("KH01", cartService.lastCustomerId);
        verify(resp).setStatus(200);
    }

    @Test
    void everyCartOperationRejectsLoginChangesAfterFilterAuthorization() throws Exception {
        ProfileResponse customer = new ProfileResponse("KH01", AccountType.CUSTOMER,
                "Customer", "customer@example.com", "0901234567", null);
        ProfileResponse anotherCustomer = new ProfileResponse("KH02", AccountType.CUSTOMER,
                "Another customer", "other@example.com", "0901234568", null);
        ProfileResponse employee = new ProfileResponse("NV01", AccountType.EMPLOYEE,
                "Admin", "admin@example.com", "0901234569", EmployeeRole.ADMIN);
        for (ProfileResponse next : new ProfileResponse[]{customer, anotherCustomer, employee}) {
            for (String method : new String[]{"doGet", "doPost", "doPut", "doDelete"}) {
                HttpServletRequest req = mockRequest(null, "KH01", null);
                HttpSession session = req.getSession(false);
                SessionAuth.capture(req); // The authentication filter binds this identity.
                SessionAuth.store(session, next); // A concurrent login completes before the servlet.
                when(req.getHeader("X-Customer-Id")).thenReturn("KH99");
                when(req.getParameter("customerId")).thenReturn("KH99");
                HttpServletResponse resp = mock(HttpServletResponse.class);
                StringWriter body = new StringWriter();
                when(resp.getWriter()).thenReturn(new PrintWriter(body));

                invokeServletMethod(method, req, resp);

                verify(resp).setStatus(401);
                assertTrue(body.toString().contains("UNAUTHORIZED"));
                assertNull(cartService.lastMethod);
                assertEquals(next, session.getAttribute("currentUser"));
                verify(session, never()).invalidate();
            }
        }
    }

    @Test
    void missingSessionAfterFilterAuthorizationCannotFallBackToClientCustomerIds() throws Exception {
        for (String method : new String[]{"doGet", "doPost", "doPut", "doDelete"}) {
            HttpServletRequest req = mockRequest(null, "KH01", null);
            SessionAuth.capture(req);
            when(req.getSession(false)).thenReturn(null); // A concurrent logout removed the session.
            when(req.getHeader("X-Customer-Id")).thenReturn("KH99");
            when(req.getParameter("customerId")).thenReturn("KH99");
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(body));

            invokeServletMethod(method, req, resp);

            verify(resp).setStatus(401);
            assertTrue(body.toString().contains("UNAUTHORIZED"));
            assertNull(cartService.lastMethod);
            verify(req, never()).getSession(true);
        }
    }

    @Test
    void employeeSessionCannotAccessCustomerCart() throws Exception {
        HttpServletRequest req = mockRequest(null, "KH01", null);
        when(req.getSession(false).getAttribute("currentUser")).thenReturn(new ProfileResponse("NV01",
                AccountType.EMPLOYEE, "Employee", "employee@example.com", "0901234567", EmployeeRole.ADMIN));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(body));

        invokeServletMethod("doGet", req, resp);

        verify(resp).setStatus(403);
        assertTrue(body.toString().contains("FORBIDDEN"));
        assertNull(cartService.lastMethod);
    }

    @Test
    void unexpectedCartFailureUsesSharedErrorEnvelopeWithoutInternalDetails() throws Exception {
        cartService.exceptionToThrow = new IllegalStateException("jdbc:mysql://private-host secret");
        HttpServletRequest req = mockRequest(null, "KH01", null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(body));

        invokeServletMethod("doGet", req, resp);

        verify(resp).setStatus(500);
        assertTrue(body.toString().contains("INTERNAL_SERVER_ERROR"));
        assertFalse(body.toString().contains("private-host"));
        assertFalse(body.toString().contains("secret"));
    }

    @Test
    @DisplayName("Yêu cầu 2: DELETE /api/cart/items thiếu cartItemId phải trả lỗi 400 BAD_REQUEST, không xóa toàn bộ giỏ")
    void testDeleteItemsWithoutIdReturnsBadRequest() throws Exception {
        HttpServletRequest req = mockRequest("/items", "KH01", "");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doDelete", req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertNull(cartService.lastMethod, "Không được gọi bất kỳ hàm xóa nào");

        String responseJson = sw.toString();
        assertTrue(responseJson.contains("\"success\":false"));
        assertTrue(responseJson.contains("BAD_REQUEST"));
        assertTrue(responseJson.contains("cartItemId"));
    }

    @Test
    @DisplayName("Yêu cầu 2: DELETE /api/cart/items có cartItemId chỉ xóa đúng món đó")
    void testDeleteItemsWithIdRemovesItem() throws Exception {
        HttpServletRequest req = mockRequest("/items", "KH01", "");
        when(req.getParameter("cartItemId")).thenReturn("CTGH01");

        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doDelete", req, resp);

        assertEquals("removeItem", cartService.lastMethod);
        assertEquals("CTGH01", cartService.lastCartItemId);
        verify(resp).setStatus(HttpServletResponse.SC_OK);
        assertTrue(sw.toString().contains("\"success\":true"));
    }

    @Test
    @DisplayName("Yêu cầu 2: DELETE /api/cart (root) hoặc /clear xóa toàn bộ giỏ hàng")
    void testDeleteCartClearsAll() throws Exception {
        HttpServletRequest req = mockRequest("/clear", "KH01", "");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doDelete", req, resp);

        assertEquals("clearCart", cartService.lastMethod);
        verify(resp).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("Yêu cầu 3: Gửi body JSON sai cú pháp trả về HTTP 400 thay vì để exception văng ra 500")
    void testInvalidJsonReturns400() throws Exception {
        HttpServletRequest req = mockRequest("/items", "KH01", "{malformed_json_no_quotes");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doPost", req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        String responseJson = sw.toString();
        assertTrue(responseJson.contains("\"success\":false"));
        assertTrue(responseJson.contains("BAD_REQUEST"));
    }

    @Test
    @DisplayName("Yêu cầu 4: Route POST /items/update gọi đúng luồng cập nhật món")
    void testPostUpdateRouteDispatchesCorrectly() throws Exception {
        String body = "{\"cartItemId\":\"CTGH01\",\"quantity\":3}";
        HttpServletRequest req = mockRequest("/items/update", "KH01", body);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doPost", req, resp);

        assertEquals("updateItem", cartService.lastMethod);
        assertNotNull(cartService.lastUpdateReq);
        assertEquals("CTGH01", cartService.lastUpdateReq.getCartItemId());
        assertEquals(3, cartService.lastUpdateReq.getQuantity());
        verify(resp).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("Yêu cầu 4: Route POST /items/remove gọi đúng luồng xóa món")
    void testPostRemoveRouteDispatchesCorrectly() throws Exception {
        HttpServletRequest req = mockRequest("/items/remove", "KH01", "{\"cartItemId\":\"CTGH01\"}");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doPost", req, resp);

        assertEquals("removeItem", cartService.lastMethod);
        assertEquals("CTGH01", cartService.lastCartItemId);
        verify(resp).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("Yêu cầu 4: Route POST /clear gọi đúng luồng làm sạch giỏ hàng")
    void testPostClearRouteDispatchesCorrectly() throws Exception {
        HttpServletRequest req = mockRequest("/clear", "KH01", "");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doPost", req, resp);

        assertEquals("clearCart", cartService.lastMethod);
        verify(resp).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("Yêu cầu 5: Số lượng không hợp lệ ném BadRequestException trả về HTTP 400")
    void testInvalidQuantityReturns400() throws Exception {
        cartService.exceptionToThrow = new BadRequestException(ErrorCode.INVALID_QUANTITY, "Số lượng món thêm vào giỏ phải lớn hơn 0");

        HttpServletRequest req = mockRequest("/items", "KH01", "{\"foodId\":\"FOOD01\",\"quantity\":-2}");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doPost", req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        String responseJson = sw.toString();
        assertTrue(responseJson.contains("\"success\":false"));
        assertTrue(responseJson.contains("INVALID_QUANTITY"));
    }

    @Test
    @DisplayName("Lỗi ResourceNotFoundException được xử lý thành HTTP 404 chuẩn JSON")
    void testResourceNotFoundReturns404() throws Exception {
        cartService.exceptionToThrow = new ResourceNotFoundException(ErrorCode.CART_ITEM_NOT_FOUND, "Món không tồn tại trong giỏ hàng");

        HttpServletRequest req = mockRequest("/items", "KH01", "");
        when(req.getParameter("cartItemId")).thenReturn("CTGH99");

        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter sw = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(sw));

        invokeServletMethod("doDelete", req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_NOT_FOUND);
        String responseJson = sw.toString();
        assertTrue(responseJson.contains("\"success\":false"));
        assertTrue(responseJson.contains("CART_ITEM_NOT_FOUND"));
    }
}
