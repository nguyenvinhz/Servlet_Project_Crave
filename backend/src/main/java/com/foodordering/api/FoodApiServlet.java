package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.service.MenuService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(name = "foodApiServlet", urlPatterns = {"/api/foods", "/api/foods/*"})
public class FoodApiServlet extends BaseApiServlet {

    private final MenuService menuService;

    public FoodApiServlet() {
        this(new MenuService());
    }

    public FoodApiServlet(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                String categoryId = request.getParameter("category_id");
                String keyword = request.getParameter("keyword");
                List<FoodSummaryResponse> foods = menuService.getMenu(categoryId, keyword);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(foods));
                return;
            }

            String id = pathInfo.substring(1);
            FoodDetailResponse food = menuService.getFoodDetail(id, true);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(food));
        } catch (Exception e) {
            handleError(response, e);
        }
    }
}
