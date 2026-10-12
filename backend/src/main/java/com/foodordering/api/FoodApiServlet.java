package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.CartConsistencyItemRequest;
import com.foodordering.dto.CartConsistencyReport;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.entity.Food;
import com.foodordering.service.MenuService;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

            if (pathInfo.endsWith("/validate")) {
                String foodId = pathInfo.substring(1, pathInfo.lastIndexOf("/validate"));
                Food food = menuService.validateFoodAvailable(foodId);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(Map.of(
                        "valid", true,
                        "foodId", food.getId(),
                        "foodName", food.getName(),
                        "status", food.getStatus().name()
                )));
                return;
            }

            String id = pathInfo.substring(1);
            FoodDetailResponse food = menuService.getFoodDetail(id, true);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(food));
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String pathInfo = request.getPathInfo();
            if ("/validate-cart".equals(pathInfo) || "/validate".equals(pathInfo)) {
                com.fasterxml.jackson.databind.JsonNode root = JsonProvider.objectMapper().readTree(request.getInputStream());
                List<CartConsistencyItemRequest> items = new ArrayList<>();
                if (root != null) {
                    com.fasterxml.jackson.databind.JsonNode itemsNode = root.isArray() ? root : root.get("items");
                    if (itemsNode != null && itemsNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode node : itemsNode) {
                            CartConsistencyItemRequest req = JsonProvider.objectMapper().treeToValue(node, CartConsistencyItemRequest.class);
                            if (req != null) {
                                items.add(req);
                            }
                        }
                    }
                }
                CartConsistencyReport report = menuService.checkMenuCartConsistency(items);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(report));
                return;
            }

            badRequest(response, "Đường dẫn POST không hợp lệ: " + pathInfo);
        } catch (Exception e) {
            handleError(response, e);
        }
    }
}
