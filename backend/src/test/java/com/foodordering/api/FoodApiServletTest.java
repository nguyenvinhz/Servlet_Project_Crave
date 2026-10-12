package com.foodordering.api;

import com.foodordering.dto.CartConsistencyItemRequest;
import com.foodordering.dto.CartConsistencyItemResult;
import com.foodordering.dto.CartConsistencyReport;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.enums.FoodStatus;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodApiServletTest {

    private MenuService menuService;
    private FoodApiServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws IOException {
        menuService = mock(MenuService.class);
        servlet = new FoodApiServlet(menuService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void getMenuReturns200AndListOfFoods() throws Exception {
        when(request.getPathInfo()).thenReturn(null);
        when(request.getParameter("category_id")).thenReturn(null);
        when(request.getParameter("keyword")).thenReturn(null);

        FoodSummaryResponse item = new FoodSummaryResponse("MA01", "DM01", "Burger", "Burger bò",
                BigDecimal.valueOf(50000), "http://img.jpg", "Ngon", FoodStatus.AVAILABLE);
        when(menuService.getMenu(null, null)).thenReturn(List.of(item));

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Burger bò"));
    }

    @Test
    void getMenuWithFiltersReturns200() throws Exception {
        when(request.getPathInfo()).thenReturn("");
        when(request.getParameter("category_id")).thenReturn("DM01");
        when(request.getParameter("keyword")).thenReturn("gà");

        FoodSummaryResponse item = new FoodSummaryResponse("MA02", "DM01", "Burger", "Burger gà",
                BigDecimal.valueOf(45000), "http://img.jpg", "Ngon", FoodStatus.AVAILABLE);
        when(menuService.getMenu("DM01", "gà")).thenReturn(List.of(item));

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Burger gà"));
    }

    @Test
    void getFoodDetailReturns200() throws Exception {
        when(request.getPathInfo()).thenReturn("/MA01");
        FoodDetailResponse detail = new FoodDetailResponse("MA01", "DM01", "Burger", "Burger bò",
                BigDecimal.valueOf(50000), "http://img.jpg", "Ngon", FoodStatus.AVAILABLE, List.of());
        when(menuService.getFoodDetail("MA01", true)).thenReturn(detail);

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("Burger bò"));
    }

    @Test
    void validateFoodReturns200WhenAvailable() throws Exception {
        when(request.getPathInfo()).thenReturn("/MA01/validate");
        Category cat = new Category("DM01", "Burger", "Burger");
        Food food = new Food("MA01", cat, "Burger bò", BigDecimal.valueOf(50000), null, null, FoodStatus.AVAILABLE);
        when(menuService.validateFoodAvailable("MA01")).thenReturn(food);

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("\"valid\":true"));
        assertTrue(responseWriter.toString().contains("Burger bò"));
    }

    @Test
    void postValidateCartReturns200AndConsistencyReport() throws Exception {
        when(request.getPathInfo()).thenReturn("/validate-cart");
        String json = """
                {
                    "items": [
                        { "foodId": "MA01", "expectedUnitPrice": 50000, "optionIds": [] }
                    ]
                }
                """;
        mockRequestBody(json);

        CartConsistencyItemResult itemResult = new CartConsistencyItemResult(
                "MA01", "Burger bò", true, true, FoodStatus.AVAILABLE, true, List.of(), true,
                BigDecimal.valueOf(50000), BigDecimal.valueOf(50000), ""
        );
        CartConsistencyReport report = new CartConsistencyReport(true, 1, 0, List.of(itemResult));
        when(menuService.checkMenuCartConsistency(any())).thenReturn(report);

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertTrue(responseWriter.toString().contains("\"allConsistent\":true"));
        assertTrue(responseWriter.toString().contains("Burger bò"));
    }

    @Test
    void postInvalidPathReturns400() throws Exception {
        when(request.getPathInfo()).thenReturn("/unknown-endpoint");

        servlet.doPost(request, response);

        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertTrue(responseWriter.toString().contains("INVALID_REQUEST"));
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
