package vn.edu.crave.controller.admin;

import vn.edu.crave.controller.BaseServlet;
import vn.edu.crave.controller.HealthServlet;
import vn.edu.crave.controller.account.*;
import vn.edu.crave.controller.menu.*;
import vn.edu.crave.controller.cart.*;
import vn.edu.crave.controller.order.*;
import vn.edu.crave.controller.payment.*;
import vn.edu.crave.controller.admin.*;

import vn.edu.crave.model.form.*;
import vn.edu.crave.model.viewmodel.*;
import vn.edu.crave.model.enums.*;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.*;
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

class AdminMenuServletTest {
    private MenuService menu;
    private AdminMenuServlet servlet;
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

    @BeforeEach void setup() throws Exception {setupRequest();menu=mock(MenuService.class);servlet=new AdminMenuServlet(menu);}
    @Test void anonymousCannotSubmitFoodAction() throws Exception {
        when(request.getServletPath()).thenReturn("/admin/menu/action");sessionAttributes.clear();servlet.doPost(request,response);
        verify(response).sendError(eq(401),anyString());verifyNoInteractions(menu);
    }
    @Test void unrelatedEmployeeCannotReadMenuManagement() throws Exception {
        signIn(EmployeeRole.HR_MANAGER);when(request.getServletPath()).thenReturn("/admin/menu");servlet.doGet(request,response);
        verify(response).sendError(eq(403),anyString());verifyNoInteractions(menu);
    }
    @Test void categoryPageForwardsDatabaseDataToJsp() throws Exception {
        signIn(EmployeeRole.MENU_MANAGER);when(request.getServletPath()).thenReturn("/admin/categories");
        var categories=List.of(new CategoryResponse("DM01","Category","Description",2));when(menu.getCategories()).thenReturn(categories);
        servlet.doGet(request,response);
        assertSame(categories,attributes.get("categories"));verify(request).getRequestDispatcher("/WEB-INF/views/admin/categories.jsp");verify(dispatcher).forward(request,response);
    }
    @Test void foodPagePassesFiltersAndOptionDataToJsp() throws Exception {
        signIn(EmployeeRole.ADMIN);when(request.getServletPath()).thenReturn("/admin/menu");
        when(request.getParameter("categoryId")).thenReturn("DM01");when(request.getParameter("keyword")).thenReturn("coffee");when(request.getParameter("status")).thenReturn("AVAILABLE");when(request.getParameter("options")).thenReturn("MA01");
        var detail=new FoodDetailResponse("MA01","DM01","Drinks","Coffee",BigDecimal.TEN,null,null,FoodStatus.AVAILABLE,List.of());when(menu.getFoodDetail("MA01",false)).thenReturn(detail);
        servlet.doGet(request,response);
        verify(menu).getAllFoodsForAdmin("DM01","coffee",FoodStatus.AVAILABLE);verify(menu).getOptionsByFoodId("MA01",false);assertSame(detail,attributes.get("optionsFood"));verify(dispatcher).forward(request,response);
    }
    @Test void createCategoryReadsFormFieldsAndRedirects() throws Exception {
        signIn(EmployeeRole.MENU_MANAGER);when(request.getServletPath()).thenReturn("/admin/categories/action");when(request.getParameter("action")).thenReturn("create");when(request.getParameter("name")).thenReturn("Drinks");when(request.getParameter("description")).thenReturn("Cold drinks");
        servlet.doPost(request,response);
        verify(menu).createCategory(new CategoryRequest("Drinks","Cold drinks"));verify(response).sendRedirect("/crave/admin/categories");assertTrue(sessionAttributes.containsKey("adminSuccess"));verify(request,never()).getInputStream();
    }
    @Test void invalidFoodPriceDoesNotMutateMenu() throws Exception {
        signIn(EmployeeRole.MENU_MANAGER);when(request.getServletPath()).thenReturn("/admin/menu/action");when(request.getParameter("action")).thenReturn("create");when(request.getParameter("price")).thenReturn("invalid");
        servlet.doPost(request,response);
        verifyNoInteractions(menu);assertTrue(sessionAttributes.containsKey("adminError"));verify(response).sendRedirect("/crave/admin/menu");
    }
    @Test void optionStatusPreservesSelectedFoodAfterRedirect() throws Exception {
        signIn(EmployeeRole.MENU_MANAGER);when(request.getServletPath()).thenReturn("/admin/menu/action");when(request.getParameter("action")).thenReturn("optionStatus");when(request.getParameter("id")).thenReturn("TC01");when(request.getParameter("foodId")).thenReturn("MA01");when(request.getParameter("status")).thenReturn("INACTIVE");
        servlet.doPost(request,response);
        verify(menu).updateFoodOptionStatus("TC01",OptionStatus.INACTIVE);verify(response).sendRedirect("/crave/admin/menu?options=MA01");
    }
}
