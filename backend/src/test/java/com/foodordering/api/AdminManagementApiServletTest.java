package com.foodordering.api;

import com.foodordering.dto.AdminEmployeeResponse;
import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.PromotionDto;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.service.AdminAccountService;
import com.foodordering.service.AdminPromotionService;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminManagementApiServletTest {
    private AdminAccountService accounts;
    private AdminPromotionService promotions;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter output;

    @BeforeEach
    void setup() throws Exception {
        accounts = mock(AdminAccountService.class);
        promotions = mock(AdminPromotionService.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));
    }

    @Test
    void anonymousAndWrongRoleCannotListManagedAccounts() throws Exception {
        AdminAccountApiServlet servlet = new AdminAccountApiServlet(accounts);
        servlet.doGet(request, response);
        verify(response).setStatus(401);
        verifyNoInteractions(accounts);

        reset(response);
        when(response.getWriter()).thenReturn(new PrintWriter(output));
        signIn(EmployeeRole.ORDER_STAFF);
        servlet.doGet(request, response);
        verify(response).setStatus(403);
        verifyNoInteractions(accounts);
    }

    @Test
    void hrEmployeeListContainsSafeDtoFields() throws Exception {
        signIn(EmployeeRole.HR_MANAGER);
        when(request.getServletPath()).thenReturn("/api/admin/employees");
        when(accounts.listEmployees()).thenReturn(List.of(new AdminEmployeeResponse("NV02", "Nhân viên",
                "staff@example.com", "0987654321", EmployeeRole.ORDER_STAFF, EmployeeStatus.WORKING, null, LocalDate.of(2026, 10, 1))));

        new AdminAccountApiServlet(accounts).doGet(request, response);

        verify(response).setStatus(200);
        assertTrue(output.toString().contains("\"role\":\"ORDER_STAFF\""));
        assertFalse(output.toString().contains("password"));
    }

    @Test
    void customerStatusPatchUsesSessionActorAndAllowedStatus() throws Exception {
        signIn(EmployeeRole.ADMIN);
        when(request.getServletPath()).thenReturn("/api/admin/customers");
        when(request.getPathInfo()).thenReturn("/KH02/status");
        body("{\"status\":\"LOCKED\"}");

        new AdminAccountApiServlet(accounts).doPatch(request, response);

        verify(accounts).updateCustomerStatus("NV01", "KH02", CustomerStatus.LOCKED);
        verify(response).setStatus(200);
    }

    @Test
    void invalidStatusOrRoleOnCustomerDoesNotReachService() throws Exception {
        signIn(EmployeeRole.ADMIN);
        when(request.getServletPath()).thenReturn("/api/admin/customers");
        when(request.getPathInfo()).thenReturn("/KH02/status");
        body("{\"status\":\"UNKNOWN\"}");

        new AdminAccountApiServlet(accounts).doPatch(request, response);

        verify(response).setStatus(400);
        verifyNoInteractions(accounts);
    }

    @Test
    void employeeCreationParsesContactAndHireDate() throws Exception {
        signIn(EmployeeRole.HR_MANAGER);
        when(request.getServletPath()).thenReturn("/api/admin/employees");
        body("""
                {"fullName":"Nhân viên mới","email":"new@example.com","phone":"0987654321",
                 "password":"Created#123","role":"ORDER_STAFF","hireDate":"2026-10-01"}
                """);

        new AdminAccountApiServlet(accounts).doPost(request, response);

        verify(accounts).createEmployee(eq("NV01"), argThat(r -> r.hireDate().equals(LocalDate.of(2026, 10, 1))
                && r.role() == EmployeeRole.ORDER_STAFF && "new@example.com".equals(r.email())));
        verify(response).setStatus(201);
    }

    @Test
    void menuManagerCannotAccessPromotions() throws Exception {
        signIn(EmployeeRole.MENU_MANAGER);

        new AdminPromotionApiServlet(promotions).doGet(request, response);

        verify(response).setStatus(403);
        verifyNoInteractions(promotions);
    }

    @Test
    void promotionManagerCanListInactivePromotions() throws Exception {
        signIn(EmployeeRole.PROMOTION_MANAGER);
        PromotionDto promotion = new PromotionDto();
        promotion.setPromotionId("KM01");
        promotion.setStatus(PromotionStatus.INACTIVE);
        when(promotions.listPromotions()).thenReturn(List.of(promotion));

        new AdminPromotionApiServlet(promotions).doGet(request, response);

        verify(response).setStatus(200);
        assertTrue(output.toString().contains("\"status\":\"INACTIVE\""));
    }

    @Test
    void promotionStatusPatchPersistsRequestedStatus() throws Exception {
        signIn(EmployeeRole.PROMOTION_MANAGER);
        when(request.getPathInfo()).thenReturn("/KM01/status");
        body("{\"status\":\"INACTIVE\"}");

        new AdminPromotionApiServlet(promotions).doPatch(request, response);

        verify(promotions).updatePromotionStatus("KM01", PromotionStatus.INACTIVE);
        verify(response).setStatus(200);
    }

    @Test
    void promotionCreationParsesDateAndDecimalFields() throws Exception {
        signIn(EmployeeRole.ADMIN);
        body("""
                {"code":"WELCOME","name":"Ưu đãi","discountType":"PERCENT","discountValue":10,
                 "minimumOrderValue":50000,"startAt":"2026-10-01T00:00:00","endAt":"2026-11-01T23:59:59",
                 "status":"ACTIVE"}
                """);

        new AdminPromotionApiServlet(promotions).doPost(request, response);

        verify(promotions).createPromotion(argThat(r -> "WELCOME".equals(r.code()) && r.discountValue().intValueExact() == 10
                && r.endAt().getMonthValue() == 11));
        verify(response).setStatus(201);
    }

    private void signIn(EmployeeRole role) {
        HttpSession session = mock(HttpSession.class);
        ProfileResponse profile = new ProfileResponse("NV01", AccountType.EMPLOYEE, "Quản lý", "manager@example.com", "0900000001", role);
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("currentUser", profile);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> { attributes.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(session).setAttribute(anyString(), any());
    }

    private void body(String json) throws Exception {
        ByteArrayInputStream bytes = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
            @Override public int read() { return bytes.read(); }
        });
    }
}
