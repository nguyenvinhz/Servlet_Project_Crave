package com.foodordering.servlet;

import com.foodordering.bean.FoodDetailBean;
import com.foodordering.bean.FoodOptionBean;
import com.foodordering.bean.PromotionBean;
import com.foodordering.bean.ProfileBean;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.service.MenuService;
import com.foodordering.service.PromotionService;
import com.foodordering.service.CartService;
import com.foodordering.security.SessionAuth;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class MenuPromotionServletTest {
    @Test
    void menuRestoresPersistedCartBadgeForFreshCustomerSession() throws Exception {
        MenuService service = mock(MenuService.class);
        CartService carts = mock(CartService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getSession(false)).thenReturn(session);
        when(request.getServletPath()).thenReturn("/menu");
        when(request.getParameter("customerId")).thenReturn("another-customer");
        when(session.getAttribute(SessionAuth.CURRENT_USER)).thenReturn(new ProfileBean("customer-1", AccountType.CUSTOMER,
                "Khách", "customer@example.com", "0900000000", null));
        when(carts.countItems("customer-1")).thenReturn(5);
        when(request.getRequestDispatcher("/WEB-INF/views/menu/index.jsp")).thenReturn(dispatcher);

        new MenuServlet(service, carts).doGet(request, response);

        verify(carts).countItems("customer-1");
        verify(session).setAttribute("cartItemCount", 5);
        verify(carts, never()).getOrCreateCart(anyString());
        verify(dispatcher).forward(request, response);
    }

    @Test
    void employeeMenuDoesNotLoadACustomerCart() throws Exception {
        MenuService service = mock(MenuService.class);
        CartService carts = mock(CartService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(SessionAuth.CURRENT_USER)).thenReturn(new ProfileBean("staff-1", AccountType.EMPLOYEE,
                "Nhân viên", "staff@example.com", "0900000000", EmployeeRole.ADMIN));
        when(request.getRequestDispatcher("/WEB-INF/views/menu/index.jsp")).thenReturn(mock(RequestDispatcher.class));
        new MenuServlet(service, carts).doGet(request, response);
        verifyNoInteractions(carts);
    }

    @Test
    void menuForwardsServerLoadedSearchResults() throws Exception {
        MenuService service = mock(MenuService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getServletPath()).thenReturn("/menu");
        when(request.getParameter("categoryId")).thenReturn("category-1");
        when(request.getParameter("keyword")).thenReturn("Cơm");
        when(request.getRequestDispatcher("/WEB-INF/views/menu/index.jsp")).thenReturn(dispatcher);

        new MenuServlet(service).doGet(request, response);

        verify(service).getMenu("category-1", "Cơm");
        verify(request).setAttribute("categories", List.of());
        verify(request).setAttribute("foods", List.of());
        verify(dispatcher).forward(request, response);
    }

    @Test
    void foodDetailForwardsOptionsWithDefaultSugarAndIceSelections() throws Exception {
        MenuService service = mock(MenuService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getServletPath()).thenReturn("/menu/detail");
        when(request.getParameter("id")).thenReturn("food-1");
        when(request.getRequestDispatcher("/WEB-INF/views/menu/detail.jsp")).thenReturn(dispatcher);
        FoodDetailBean food = new FoodDetailBean("food-1", "category-1", "Đồ uống", "Trà", BigDecimal.TEN,
                null, null, FoodStatus.AVAILABLE, List.of(
                        new FoodOptionBean("sugar-1", "food-1", OptionType.SUGAR_LEVEL, "Ít đường", BigDecimal.ZERO, OptionStatus.ACTIVE),
                        new FoodOptionBean("ice-1", "food-1", OptionType.ICE_LEVEL, "Ít đá", BigDecimal.ZERO, OptionStatus.ACTIVE)));
        when(service.getFoodDetail("food-1", true)).thenReturn(food);

        new MenuServlet(service).doGet(request, response);

        verify(request).setAttribute("food", food);
        verify(request).setAttribute("selectedOptionIds", Set.of("sugar-1", "ice-1"));
        verify(dispatcher).forward(request, response);
    }

    @Test
    void promotionsAreAvailableAsJspAttribute() throws Exception {
        PromotionService service = mock(PromotionService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        PromotionBean promotion = new PromotionBean();
        promotion.setCode("SALE");
        when(service.getActivePromotions()).thenReturn(List.of(promotion));
        when(request.getRequestDispatcher("/WEB-INF/views/promotions/index.jsp")).thenReturn(dispatcher);

        new PromotionServlet(service).doGet(request, response);

        verify(request).setAttribute("promotions", List.of(promotion));
        verify(dispatcher).forward(request, response);
        verify(response, never()).getWriter();
    }
}
