package com.foodordering.servlet;
import com.foodordering.dto.*;
import com.foodordering.enums.*;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.*;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class AdminOrderFormServletTest {
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;
    private final Map<String,Object> attributes=new HashMap<>(),sessionAttributes=new HashMap<>();
    private void setupRequest() throws Exception {
        attributes.clear();sessionAttributes.clear();request=mock(HttpServletRequest.class);response=mock(HttpServletResponse.class);session=mock(HttpSession.class);dispatcher=mock(RequestDispatcher.class);
        when(request.getContextPath()).thenReturn("/crave");when(request.getSession()).thenReturn(session);when(request.getSession(false)).thenReturn(session);
        when(request.getAttribute(anyString())).thenAnswer(call->attributes.get(call.getArgument(0)));
        doAnswer(call->{attributes.put(call.getArgument(0),call.getArgument(1));return null;}).when(request).setAttribute(anyString(),any());
        when(session.getAttribute(anyString())).thenAnswer(call->sessionAttributes.get(call.getArgument(0)));
        doAnswer(call->{sessionAttributes.put(call.getArgument(0),call.getArgument(1));return null;}).when(session).setAttribute(anyString(),any());
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
    }
    private void signIn(EmployeeRole role) {
        sessionAttributes.put(SessionAuth.CURRENT_USER,new ProfileResponse("NV01",AccountType.EMPLOYEE,"Employee","employee@example.com","0901234567",role));
    }


    @BeforeEach void setup() throws Exception {setupRequest();}
    @Test void orderStatusFormUsesAuthorizedEmployeeAndRedirectsToDetail() throws Exception {
        signIn(EmployeeRole.ORDER_STAFF);CustomerOrderService orders=mock(CustomerOrderService.class);
        when(request.getParameter("orderId")).thenReturn("DH01");when(request.getParameter("status")).thenReturn("PREPARING");when(request.getParameter("note")).thenReturn("Confirmed");
        new AdminOrderServlet(orders).doPost(request,response);
        verify(orders).updateOrderStatus("DH01",OrderStatus.PREPARING,"NV01","Confirmed");verify(response).sendRedirect("/crave/admin/orders?detail=DH01");assertTrue(sessionAttributes.containsKey("adminOrderMessage"));verify(request,never()).getInputStream();
    }
    @Test void wrongRoleCannotUpdateAnOrder() throws Exception {
        signIn(EmployeeRole.HR_MANAGER);CustomerOrderService orders=mock(CustomerOrderService.class);when(request.getParameter("orderId")).thenReturn("DH01");
        new AdminOrderServlet(orders).doPost(request,response);verifyNoInteractions(orders);verify(response).sendError(eq(403),anyString());assertFalse(sessionAttributes.containsKey("adminOrderMessage"));
    }
    @Test void paymentFormPassesIdentifierStatusAndReference() throws Exception {
        signIn(EmployeeRole.ADMIN);PaymentService payments=mock(PaymentService.class);
        when(request.getParameter("orderId")).thenReturn("DH01");when(request.getParameter("paymentId")).thenReturn("TT01");when(request.getParameter("status")).thenReturn("SUCCESS");when(request.getParameter("transactionRef")).thenReturn("ADMIN_123");
        new PaymentServlet(payments).doPost(request,response);verify(payments).updatePaymentStatus("TT01",PaymentStatus.SUCCESS,"ADMIN_123");verify(response).sendRedirect("/crave/admin/orders?detail=DH01");assertTrue(sessionAttributes.containsKey("adminOrderMessage"));
    }
    @Test void invalidPaymentStatusDoesNotCallService() throws Exception {
        signIn(EmployeeRole.ORDER_STAFF);PaymentService payments=mock(PaymentService.class);when(request.getParameter("orderId")).thenReturn("DH01");when(request.getParameter("status")).thenReturn("INVALID");
        new PaymentServlet(payments).doPost(request,response);verifyNoInteractions(payments);assertTrue(sessionAttributes.containsKey("adminOrderError"));verify(response).sendRedirect("/crave/admin/orders?detail=DH01");
    }
}
