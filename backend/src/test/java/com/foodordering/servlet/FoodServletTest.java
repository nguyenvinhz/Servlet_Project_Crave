package com.foodordering.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class FoodServletTest {
    @Test
    void foodLinksRedirectToTheRenderedDetailPage() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getPathInfo()).thenReturn("/MA01");
        new FoodServlet().doGet(request, response);
        verify(response).sendRedirect("/crave/menu/detail?id=MA01");
    }

    @Test
    void categoryLinksRedirectToTheFilteredMenu() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn("/crave");
        when(request.getPathInfo()).thenReturn("/DM01");
        new CategoryServlet().doGet(request, response);
        verify(response).sendRedirect("/crave/menu?categoryId=DM01");
    }
}