package com.foodordering.servlet.admin;

import com.foodordering.bean.CategoryForm;
import com.foodordering.bean.CategoryBean;
import com.foodordering.bean.FoodOptionBean;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.exception.AccountException;
import com.foodordering.service.MenuService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminMenuServletTest extends AdminFormTestSupport {
    private MenuService menu;
    private AdminMenuServlet servlet;
    @BeforeEach void setupServlet() { menu = mock(MenuService.class); servlet = new AdminMenuServlet(menu); }

    @Test void categoriesAreRequestAttributesAndForwardToJsp() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/categories");
        var categories = List.of(new CategoryBean("DM01", "Burger", "Món chính", 5));
        when(menu.getCategories()).thenReturn(categories);
        servlet.doGet(request, response);
        assertSame(categories, attributes.get("categories"));
        verify(request).getRequestDispatcher("/WEB-INF/views/admin/categories.jsp");
        verify(dispatcher).forward(request, response);
    }

    @Test void menuUsesGetFilterParameters() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/menu");
        parameters.putAll(java.util.Map.of("categoryId", "DM01", "keyword", "Gà", "status", "AVAILABLE"));
        servlet.doGet(request, response);
        verify(menu).getAllFoodsForAdmin("DM01", "Gà", FoodStatus.AVAILABLE);
        verify(dispatcher).forward(request, response);
    }

    @Test void categoryCreationReadsParametersAndRedirects() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/categories");
        parameters.putAll(java.util.Map.of("action", "create", "name", "Món chính", "description", "Ăn trưa"));
        servlet.doPost(request, response);
        verify(menu).createCategory(new CategoryForm("Món chính", "Ăn trưa"));
        verify(response).sendRedirect("/crave/admin/categories");
        verify(request, never()).getInputStream();
    }

    @Test void foodCreationConvertsDecimalAndEnumFormFields() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/menu");
        parameters.putAll(java.util.Map.of("action", "create", "categoryId", "DM01", "name", "Cơm gà", "price", "45000", "status", "AVAILABLE"));
        servlet.doPost(request, response);
        verify(menu).createFood(argThat(form -> form.categoryId().equals("DM01") && form.price().compareTo(new BigDecimal("45000")) == 0
                && form.status() == FoodStatus.AVAILABLE));
        verify(response).sendRedirect("/crave/admin/menu");
    }

    @Test void invalidAmountRedirectsWithValidationMessageWithoutWrite() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/menu");
        parameters.putAll(java.util.Map.of("action", "create", "price", "invalid"));
        servlet.doPost(request, response);
        verify(menu, never()).createFood(any());
        assertNotNull(sessionAttributes.get("adminError"));
        verify(response).sendRedirect("/crave/admin/menu");
    }

    @Test void optionCreationUsesFoodAndOptionFormParameters() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/menu");
        parameters.putAll(java.util.Map.of("action", "optionCreate", "foodId", "F01", "optionType", "TOPPING", "name", "Trứng", "extraPrice", "5000", "status", "ACTIVE"));
        servlet.doPost(request, response);
        verify(menu).createFoodOption(eq("F01"), argThat(form -> form.optionType() == OptionType.TOPPING && form.extraPrice().intValueExact() == 5000));
        verify(response).sendRedirect("/crave/admin/menu?foodId=F01");
    }

    @Test void cannotMutateAnOptionFromAnotherFood() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/menu");
        when(menu.getFoodOptionById("OPT01")).thenReturn(new FoodOptionBean("OPT01", "F02", OptionType.SIZE, "L", BigDecimal.ZERO, OptionStatus.ACTIVE));
        parameters.putAll(java.util.Map.of("action", "optionDelete", "foodId", "F01", "id", "OPT01"));
        servlet.doPost(request, response);
        verify(menu, never()).deleteFoodOption(anyString());
        assertNotNull(sessionAttributes.get("adminError"));
    }

    @Test void nonMenuStaffCannotReadOrWriteMenu() {
        signIn(EmployeeRole.ORDER_STAFF);
        assertEquals(403, assertThrows(AccountException.class, () -> servlet.doGet(request, response)).getStatusCode());
        assertThrows(AccountException.class, () -> servlet.doPost(request, response));
        verifyNoInteractions(menu);
    }
}
