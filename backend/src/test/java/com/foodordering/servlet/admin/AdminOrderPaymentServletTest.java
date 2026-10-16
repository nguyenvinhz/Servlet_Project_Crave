package com.foodordering.servlet.admin;

import com.foodordering.bean.ProfileBean;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.CustomerOrderService;
import com.foodordering.service.PaymentService;
import com.foodordering.service.PayosPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class AdminOrderPaymentServletTest {
    private final ProfileBean employee = new ProfileBean("NV01", AccountType.EMPLOYEE, "Nhân viên",
            "staff@example.com", "0901234568", EmployeeRole.ORDER_STAFF);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final HttpSession session = mock(HttpSession.class);
    private final CustomerOrderService orders = mock(CustomerOrderService.class);
    private final PayosPaymentService payos = mock(PayosPaymentService.class);

    AdminOrderPaymentServletTest() {
        when(request.getSession()).thenReturn(session);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/crave");
    }

    @Test void adminStatusUsesEmployeeFromSession() throws Exception {
        when(request.getParameter("orderId")).thenReturn("DH01");
        when(request.getParameter("status")).thenReturn("PREPARING");
        when(request.getParameter("note")).thenReturn("Đã xác nhận");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF)).thenReturn(employee);
            new AdminOrderServlet(orders, payos).doPost(request, response);
        }
        verify(orders).updateOrderStatus("DH01", OrderStatus.PREPARING, "NV01", "Đã xác nhận");
        verify(response).sendRedirect("/crave/admin/orders");
    }

    @Test void unauthorizedEmployeeCannotChangeOrder() throws Exception {
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF))
                    .thenThrow(new AccountException("FORBIDDEN", "Không có quyền", 403));
            new AdminOrderServlet(orders, payos).doPost(request, response);
        }
        verifyNoInteractions(orders, payos);
    }

    @Test void paymentStatusReadsFormParameters() throws Exception {
        var payments = mock(PaymentService.class);
        when(request.getParameter("paymentId")).thenReturn("TT01");
        when(request.getParameter("status")).thenReturn("SUCCESS");
        when(request.getParameter("transactionRef")).thenReturn("TX01");
        try (var auth = mockStatic(SessionAuth.class)) {
            auth.when(() -> SessionAuth.requireEmployee(request, EmployeeRole.ORDER_STAFF)).thenReturn(employee);
            new AdminPaymentServlet(payments).doPost(request, response);
        }
        verify(payments).updatePaymentStatus("TT01", PaymentStatus.SUCCESS, "TX01");
        verify(response).sendRedirect("/crave/admin/orders");
    }
}
