package com.foodordering.api;

import com.foodordering.dto.CategoryRequest;
import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodOptionResponse;
import com.foodordering.dto.FoodRequest;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.service.MenuService;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminMenuApiServletTest {

    private MenuService menuService;
    private AdminMenuApiServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws IOException {
        menuService = mock(MenuService.class);
        servlet = new AdminMenuApiServlet(menuService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void getCategoriesReturns200AndJson() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/categories");
        when(request.getPathInfo()).thenReturn(null);
        when(menuService.getCategories()).thenReturn(List.of(new CategoryResponse("DM01", "Burger", "Burger", 5)));

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Burger"));
    }

    @Test
    void postCategoryReturns201Created() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/categories");
        when(request.getPathInfo()).thenReturn(null);
        mockRequestBody("{\"name\":\"Món mới\",\"description\":\"Mô tả\"}");

        when(menuService.createCategory(any())).thenReturn(new CategoryResponse("DM02", "Món mới", "Mô tả", 0));

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_CREATED);
        assertTrue(responseWriter.toString().contains("Món mới"));
    }

    @Test
    void putCategoryReturns200Ok() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/categories");
        when(request.getPathInfo()).thenReturn("/DM01");
        mockRequestBody("{\"name\":\"Burger sửa\",\"description\":\"Mô tả mới\"}");

        when(menuService.updateCategory(eq("DM01"), any())).thenReturn(new CategoryResponse("DM01", "Burger sửa", "Mô tả mới", 2));

        servlet.doPut(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Burger sửa"));
    }

    @Test
    void deleteCategoryReturns200Ok() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/categories");
        when(request.getPathInfo()).thenReturn("/DM01");

        servlet.doDelete(request, response);

        verify(menuService).deleteCategory("DM01");
        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Đã xóa danh mục thành công"));
    }

    @Test
    void getFoodsReturns200AndList() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/foods");
        when(request.getPathInfo()).thenReturn(null);
        when(menuService.getAllFoodsForAdmin(null, null, null))
                .thenReturn(List.of(new FoodSummaryResponse("MA01", "DM01", "Burger", "Burger bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE)));

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Burger bò"));
    }

    @Test
    void postFoodReturns201Created() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/foods");
        when(request.getPathInfo()).thenReturn(null);
        mockRequestBody("{\"categoryId\":\"DM01\",\"name\":\"Gà rán\",\"price\":35000,\"status\":\"AVAILABLE\"}");

        when(menuService.createFood(any())).thenReturn(new FoodDetailResponse("MA02", "DM01", "Burger", "Gà rán", BigDecimal.valueOf(35000), null, null, FoodStatus.AVAILABLE, Collections.emptyList()));

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_CREATED);
        assertTrue(responseWriter.toString().contains("Gà rán"));
    }

    @Test
    void putFoodStatusReturns200Ok() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/foods");
        when(request.getPathInfo()).thenReturn("/MA01/status");
        mockRequestBody("{\"status\":\"UNAVAILABLE\"}");

        when(menuService.updateFoodStatus(eq("MA01"), eq(FoodStatus.UNAVAILABLE)))
                .thenReturn(new FoodDetailResponse("MA01", "DM01", "Burger", "Burger bò", BigDecimal.valueOf(50000), null, null, FoodStatus.UNAVAILABLE, Collections.emptyList()));

        servlet.doPut(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("UNAVAILABLE"));
    }

    @Test
    void postFoodOptionReturns201Created() throws Exception {
        when(request.getServletPath()).thenReturn("/api/admin/foods");
        when(request.getPathInfo()).thenReturn("/MA01/options");
        mockRequestBody("{\"optionType\":\"SIZE\",\"name\":\"Size XL\",\"extraPrice\":10000,\"status\":\"ACTIVE\"}");

        when(menuService.createFoodOption(eq("MA01"), any()))
                .thenReturn(new FoodOptionResponse("TC01", "MA01", OptionType.SIZE, "Size XL", BigDecimal.valueOf(10000), OptionStatus.ACTIVE));

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_CREATED);
        assertTrue(responseWriter.toString().contains("Size XL"));
    }

    private void mockRequestBody(String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ServletInputStream sis = new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() {
                return bais.read();
            }
        };
        when(request.getInputStream()).thenReturn(sis);
    }
}
