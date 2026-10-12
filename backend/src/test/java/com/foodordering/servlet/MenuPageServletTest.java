package com.foodordering.servlet;

import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.enums.FoodStatus;
import com.foodordering.service.MenuService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.mockito.Mockito.*;

class MenuPageServletTest {
    private final MenuService service = mock(MenuService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final RequestDispatcher dispatcher = mock(RequestDispatcher.class);

    private List<FoodSummaryResponse> foods(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new FoodSummaryResponse(
                "F" + i, i % 2 == 0 ? "C2" : "C1", "Danh mục", "Món " + i,
                BigDecimal.valueOf(15000), "/photos/food-" + i + ".jpg", "Mô tả", FoodStatus.AVAILABLE)).toList();
    }

    private void render() throws Exception {
        when(request.getServletPath()).thenReturn("/menu");
        when(request.getRequestDispatcher("/WEB-INF/views/menu/index.jsp")).thenReturn(dispatcher);
        MenuPageServlet servlet = new MenuPageServlet();
        servlet.setMenuService(service);
        servlet.doGet(request, response);
        verify(dispatcher).forward(request, response);
    }

    @Test
    void secondPageShowsTwelveFoodsAndGalleryStillIncludesOtherPagesAndCategories() throws Exception {
        List<FoodSummaryResponse> foods = foods(30);
        when(service.getMenu(null, null)).thenReturn(foods);
        when(request.getParameter("page")).thenReturn("2");
        render();
        verify(request).setAttribute("foods", foods.subList(12, 24));
        verify(request).setAttribute("pageStart", 13);
        verify(request).setAttribute("pageEnd", 24);
        verify(request).setAttribute("totalPages", 3);
        verify(request).setAttribute(eq("categoryFoods"), argThat(value ->
                value instanceof Map<?, ?> gallery
                        && ((List<?>) gallery.get("C1")).size() == 15
                        && ((List<?>) gallery.get("C2")).size() == 15));
    }

    @Test
    void filteringPreservesQueryAndUsesIndependentGallery() throws Exception {
        List<FoodSummaryResponse> all = foods(30);
        List<FoodSummaryResponse> filtered = all.stream().filter(food -> food.categoryId().equals("C2")).toList();
        when(service.getMenu(null, null)).thenReturn(all);
        when(service.getMenu("C2", "Món")).thenReturn(filtered);
        when(request.getParameter("categoryId")).thenReturn("C2");
        when(request.getParameter("keyword")).thenReturn("Món");
        when(request.getParameter("page")).thenReturn("999");
        render();
        verify(request).setAttribute("foods", filtered.subList(12, 15));
        verify(request).setAttribute("currentPage", 2);
        verify(request).setAttribute("currentCategoryId", "C2");
        verify(request).setAttribute("keyword", "Món");
        verify(request).setAttribute("totalFoods", 15);
    }

    @Test
    void invalidPageAndEmptySearchHaveSafeBounds() throws Exception {
        when(service.getMenu(null, null)).thenReturn(List.of());
        when(request.getParameter("page")).thenReturn("not-a-number");
        render();
        verify(request).setAttribute("foods", List.of());
        verify(request).setAttribute("currentPage", 1);
        verify(request).setAttribute("totalPages", 1);
        verify(request).setAttribute("pageStart", 0);
        verify(request).setAttribute("pageEnd", 0);
    }
}
