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

class AdminManagementServletTest {
    private AdminAccountService accounts;
    private AdminPromotionService promotions;
    private AdminOverviewService overview;
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

    @BeforeEach void setup() throws Exception {setupRequest();accounts=mock(AdminAccountService.class);promotions=mock(AdminPromotionService.class);overview=mock(AdminOverviewService.class);}
    @Test void wrongRoleCannotReadEmployeePage() throws Exception {
        signIn(EmployeeRole.ORDER_STAFF);when(request.getServletPath()).thenReturn("/admin/employees");new AdminPageServlet(accounts,promotions,overview).doGet(request,response);
        verify(response).sendError(eq(403),anyString());verifyNoInteractions(accounts);
    }
    @Test void hrListIsRequestAttributeAndJspForward() throws Exception {
        signIn(EmployeeRole.HR_MANAGER);when(request.getServletPath()).thenReturn("/admin/employees");
        var employees=List.of(new AdminEmployeeResponse("NV02","Employee","staff@example.com","0987654321",EmployeeRole.ORDER_STAFF,EmployeeStatus.WORKING,null,LocalDate.of(2026,10,1)));when(accounts.listEmployees()).thenReturn(employees);
        new AdminPageServlet(accounts,promotions,overview).doGet(request,response);
        assertSame(employees,attributes.get("accounts"));verify(request).getRequestDispatcher("/WEB-INF/views/admin/employees.jsp");verify(dispatcher).forward(request,response);
    }
    @Test void customerStatusUsesSessionActorAndFormParameters() throws Exception {
        signIn(EmployeeRole.ADMIN);when(request.getServletPath()).thenReturn("/admin/customers/action");when(request.getParameter("action")).thenReturn("status");when(request.getParameter("id")).thenReturn("KH02");when(request.getParameter("status")).thenReturn("LOCKED");
        new AdminAccountServlet(accounts).doPost(request,response);
        verify(accounts).updateCustomerStatus("NV01","KH02",CustomerStatus.LOCKED);verify(response).sendRedirect("/crave/admin/customers");verify(request,never()).getInputStream();
    }
    @Test void roleUpdateCannotBeUsedByMenuManager() throws Exception {
        signIn(EmployeeRole.MENU_MANAGER);when(request.getServletPath()).thenReturn("/admin/employees/action");when(request.getParameter("action")).thenReturn("role");
        new AdminAccountServlet(accounts).doPost(request,response);verify(response).sendError(eq(403),anyString());verifyNoInteractions(accounts);
    }
    @Test void employeeCreationParsesDateAndRoleFromForm() throws Exception {
        signIn(EmployeeRole.HR_MANAGER);when(request.getServletPath()).thenReturn("/admin/employees/action");when(request.getParameter("action")).thenReturn("create");when(request.getParameter("fullName")).thenReturn("New employee");when(request.getParameter("email")).thenReturn("staff@example.com");when(request.getParameter("phone")).thenReturn("0987654321");when(request.getParameter("password")).thenReturn("Password123");when(request.getParameter("role")).thenReturn("ORDER_STAFF");when(request.getParameter("hireDate")).thenReturn("2026-10-16");
        new AdminAccountServlet(accounts).doPost(request,response);ArgumentCaptor<EmployeeCreateRequest> captor=ArgumentCaptor.forClass(EmployeeCreateRequest.class);verify(accounts).createEmployee(eq("NV01"),captor.capture());assertEquals(EmployeeRole.ORDER_STAFF,captor.getValue().role());assertEquals(LocalDate.of(2026,10,16),captor.getValue().hireDate());verify(response).sendRedirect("/crave/admin/employees");
    }
    @Test void promotionFormParsesAmountAndOptionalMaximum() throws Exception {
        signIn(EmployeeRole.PROMOTION_MANAGER);when(request.getParameter("action")).thenReturn("create");when(request.getParameter("code")).thenReturn("WELCOME");when(request.getParameter("name")).thenReturn("Welcome");when(request.getParameter("discountType")).thenReturn("PERCENT");when(request.getParameter("discountValue")).thenReturn("10");when(request.getParameter("minimumOrderValue")).thenReturn("50000");when(request.getParameter("maximumDiscount")).thenReturn("");when(request.getParameter("startAt")).thenReturn("2026-10-16T08:00");when(request.getParameter("endAt")).thenReturn("2026-10-30T23:59");when(request.getParameter("status")).thenReturn("ACTIVE");
        new AdminPromotionServlet(promotions).doPost(request,response);ArgumentCaptor<AdminPromotionRequest> captor=ArgumentCaptor.forClass(AdminPromotionRequest.class);verify(promotions).createPromotion(captor.capture());assertEquals(new BigDecimal("10"),captor.getValue().discountValue());assertNull(captor.getValue().maximumDiscount());verify(response).sendRedirect("/crave/admin/promotions");
    }
    @Test void invalidStatusReturnsFlashWithoutMutation() throws Exception {
        signIn(EmployeeRole.PROMOTION_MANAGER);when(request.getParameter("action")).thenReturn("status");when(request.getParameter("status")).thenReturn("invalid");new AdminPromotionServlet(promotions).doPost(request,response);verifyNoInteractions(promotions);assertTrue(sessionAttributes.containsKey("adminError"));verify(response).sendRedirect("/crave/admin/promotions");
    }
    @Test void reportDateRangeIsLoadedAndForwarded() throws Exception {
        signIn(EmployeeRole.ADMIN);when(request.getParameter("from")).thenReturn("2026-10-01");when(request.getParameter("to")).thenReturn("2026-10-16");Map<String,Object> report=Map.of("daily",List.of());when(overview.overview(LocalDate.of(2026,10,1),LocalDate.of(2026,10,16))).thenReturn(report);
        new AdminOverviewServlet(overview).doGet(request,response);assertSame(report,attributes.get("report"));verify(request).getRequestDispatcher("/WEB-INF/views/admin/reports.jsp");verify(dispatcher).forward(request,response);
    }
    @Test void invalidReportDateDoesNotQueryDatabase() throws Exception {
        signIn(EmployeeRole.ADMIN);when(request.getParameter("from")).thenReturn("2026-99-99");new AdminOverviewServlet(overview).doGet(request,response);verifyNoInteractions(overview);verify(response).sendError(eq(400),anyString());
    }

    @Test void invalidEmployeeHireDateReturnsFormFeedbackBeforePersistence() throws Exception {
        for (String date : new String[]{"2026-99-99", "not-a-date", ""}) {
            setupRequest();
            signIn(EmployeeRole.HR_MANAGER);
            when(request.getServletPath()).thenReturn("/admin/employees/action");
            when(request.getParameter("action")).thenReturn("create");
            when(request.getParameter("role")).thenReturn("ORDER_STAFF");
            when(request.getParameter("hireDate")).thenReturn(date);

            new AdminAccountServlet(accounts).doPost(request,response);

            verifyNoInteractions(accounts);
            assertNotNull(sessionAttributes.get("adminError"));
            assertFalse(((String)sessionAttributes.get("adminError")).isBlank());
            assertFalse(sessionAttributes.containsKey("adminSuccess"));
            verify(response).sendRedirect("/crave/admin/employees");
            verify(response,never()).sendError(eq(500),anyString());
        }
    }

    @Test void invalidPromotionStartOrEndDateReturnsFormFeedbackBeforePersistence() throws Exception {
        for (String invalidField : new String[]{"startAt", "endAt"}) {
            for (String invalidDate : new String[]{"2026-99-99T08:00", "not-a-date", ""}) {
                setupRequest();
                signIn(EmployeeRole.PROMOTION_MANAGER);
                when(request.getParameter("action")).thenReturn("create");
                when(request.getParameter("discountType")).thenReturn("PERCENT");
                when(request.getParameter("discountValue")).thenReturn("10");
                when(request.getParameter("startAt")).thenReturn("2026-10-17T08:00");
                when(request.getParameter("endAt")).thenReturn("2026-10-30T23:59");
                when(request.getParameter("status")).thenReturn("ACTIVE");
                when(request.getParameter(invalidField)).thenReturn(invalidDate);

                new AdminPromotionServlet(promotions).doPost(request,response);

                verifyNoInteractions(promotions);
                assertNotNull(sessionAttributes.get("adminError"));
                assertFalse(((String)sessionAttributes.get("adminError")).isBlank());
                assertFalse(sessionAttributes.containsKey("adminSuccess"));
                verify(response).sendRedirect("/crave/admin/promotions");
                verify(response,never()).sendError(eq(500),anyString());
            }
        }
    }
}
