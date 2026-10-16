package com.foodordering.servlet.admin;

import com.foodordering.bean.AdminEmployeeBean;
import com.foodordering.enums.CustomerStatus;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.AccountException;
import com.foodordering.service.AdminAccountService;
import com.foodordering.service.AdminPromotionService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminManagementServletTest extends AdminFormTestSupport {
    @Test void employeeListIsFilteredBeforeJspForward() throws Exception {
        var accounts = mock(AdminAccountService.class);
        signIn(EmployeeRole.HR_MANAGER);
        when(request.getServletPath()).thenReturn("/admin/employees");
        parameters.put("keyword", "staff");
        when(accounts.listEmployees()).thenReturn(List.of(new AdminEmployeeBean("NV02", "Nhân viên", "staff@example.com", "0987654321",
                EmployeeRole.ORDER_STAFF, EmployeeStatus.WORKING, null, LocalDate.of(2026, 10, 1))));
        new AdminAccountServlet(accounts).doGet(request, response);
        assertEquals(1, ((List<?>) attributes.get("employees")).size());
        assertFalse(((List<?>) attributes.get("roles")).contains(EmployeeRole.ADMIN));
        verify(request).getRequestDispatcher("/WEB-INF/views/admin/employees.jsp");
        verify(dispatcher).forward(request, response);
    }

    @Test void customerStatusFormUsesAuthenticatedActor() throws Exception {
        var accounts = mock(AdminAccountService.class);
        when(request.getServletPath()).thenReturn("/admin/customers");
        parameters.putAll(Map.of("action", "status", "id", "KH02", "status", "LOCKED"));
        new AdminAccountServlet(accounts).doPost(request, response);
        verify(accounts).updateCustomerStatus("NV01", "KH02", CustomerStatus.LOCKED);
        verify(response).sendRedirect("/crave/admin/customers");
    }

    @Test void employeeCreationPreservesPasswordAndParsesHireDate() throws Exception {
        var accounts = mock(AdminAccountService.class);
        when(request.getServletPath()).thenReturn("/admin/employees");
        parameters.putAll(Map.of("action", "create", "fullName", "Nhân viên mới", "email", "new@example.com", "phone", "0987654321",
                "password", " Created#123 ", "role", "MENU_MANAGER", "hireDate", "2026-10-12"));
        new AdminAccountServlet(accounts).doPost(request, response);
        verify(accounts).createEmployee(eq("NV01"), argThat(form -> form.password().equals(" Created#123 ")
                && form.hireDate().equals(LocalDate.of(2026, 10, 12)) && form.role() == EmployeeRole.MENU_MANAGER));
    }

    @Test void employeeRoleUsesPostForm() throws Exception {
        var accounts = mock(AdminAccountService.class);
        when(request.getServletPath()).thenReturn("/admin/employees");
        parameters.putAll(Map.of("action", "role", "id", "NV02", "role", "HR_MANAGER"));
        new AdminAccountServlet(accounts).doPost(request, response);
        verify(accounts).updateEmployeeRole("NV01", "NV02", EmployeeRole.HR_MANAGER);
        verify(response).sendRedirect("/crave/admin/employees");
    }

    @Test void wrongRoleCannotListAccounts() {
        var accounts = mock(AdminAccountService.class);
        signIn(EmployeeRole.ORDER_STAFF);
        assertEquals(403, assertThrows(AccountException.class, () -> new AdminAccountServlet(accounts).doGet(request, response)).getStatusCode());
        verifyNoInteractions(accounts);
    }

    @Test void invalidAccountStatusIsAFlashErrorWithoutServiceMutation() throws Exception {
        var accounts = mock(AdminAccountService.class);
        when(request.getServletPath()).thenReturn("/admin/customers");
        parameters.putAll(Map.of("action", "status", "id", "KH02", "status", "UNKNOWN"));
        new AdminAccountServlet(accounts).doPost(request, response);
        assertNotNull(sessionAttributes.get("adminError"));
        verifyNoInteractions(accounts);
    }

    @Test void promotionCreationConvertsFormDateAndDecimalValues() throws Exception {
        var promotions = mock(AdminPromotionService.class);
        when(request.getServletPath()).thenReturn("/admin/promotions");
        parameters.putAll(Map.of("action", "create", "code", "WELCOME", "name", "Ưu đãi", "discountType", "PERCENT", "discountValue", "10",
                "minimumOrderValue", "50000", "startAt", "2026-10-01T00:00", "endAt", "2026-11-01T23:59", "status", "ACTIVE"));
        new AdminPromotionServlet(promotions).doPost(request, response);
        verify(promotions).createPromotion(argThat(form -> form.code().equals("WELCOME") && form.discountValue().intValueExact() == 10
                && form.endAt().getMonthValue() == 11 && form.maximumDiscount() == null));
        verify(response).sendRedirect("/crave/admin/promotions");
        verify(request, never()).getInputStream();
    }

    @Test void promotionStatusIsPostFormAndRespectsRole() throws Exception {
        var promotions = mock(AdminPromotionService.class);
        signIn(EmployeeRole.PROMOTION_MANAGER);
        when(request.getServletPath()).thenReturn("/admin/promotions");
        parameters.putAll(Map.of("action", "status", "id", "KM01", "status", "INACTIVE"));
        new AdminPromotionServlet(promotions).doPost(request, response);
        verify(promotions).updatePromotionStatus("KM01", PromotionStatus.INACTIVE);
    }
}
