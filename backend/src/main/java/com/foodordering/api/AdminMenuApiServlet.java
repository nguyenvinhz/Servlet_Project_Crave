package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.CreateCategoryRequest;
import com.foodordering.dto.CreateFoodOptionRequest;
import com.foodordering.dto.CreateFoodRequest;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodOptionResponse;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.dto.UpdateCategoryRequest;
import com.foodordering.dto.UpdateFoodOptionRequest;
import com.foodordering.dto.UpdateFoodRequest;
import com.foodordering.enums.FoodStatus;
import com.foodordering.service.MenuService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(name = "adminMenuApiServlet", urlPatterns = {
        "/api/admin/categories",
        "/api/admin/categories/*",
        "/api/admin/foods",
        "/api/admin/foods/*",
        "/api/admin/options/*"
})
public class AdminMenuApiServlet extends BaseApiServlet {

    private final MenuService menuService;

    public AdminMenuApiServlet() {
        this(new MenuService());
    }

    public AdminMenuApiServlet(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();

            if (servletPath.startsWith("/api/admin/categories")) {
                handleGetCategory(request, response, pathInfo);
            } else if (servletPath.startsWith("/api/admin/foods")) {
                handleGetFood(request, response, pathInfo);
            } else {
                badRequest(response, "Đường dẫn không hợp lệ.");
            }
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();

            if (servletPath.equals("/api/admin/categories")) {
                CreateCategoryRequest req = readJson(request, CreateCategoryRequest.class);
                CategoryResponse created = menuService.createCategory(req);
                writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
            } else if (servletPath.equals("/api/admin/foods")) {
                CreateFoodRequest req = readJson(request, CreateFoodRequest.class);
                FoodDetailResponse created = menuService.createFood(req);
                writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
            } else if (servletPath.startsWith("/api/admin/foods") && pathInfo != null && pathInfo.endsWith("/options")) {
                // /api/admin/foods/{id}/options
                String[] parts = pathInfo.split("/");
                if (parts.length >= 3) {
                    String foodId = parts[1];
                    CreateFoodOptionRequest req = readJson(request, CreateFoodOptionRequest.class);
                    FoodOptionResponse created = menuService.addFoodOption(foodId, req);
                    writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
                    return;
                }
                badRequest(response, "Mã món ăn không hợp lệ.");
            } else {
                badRequest(response, "Đường dẫn không hợp lệ.");
            }
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();

            if (servletPath.startsWith("/api/admin/categories")) {
                if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                    badRequest(response, "Thiếu mã danh mục cần cập nhật.");
                    return;
                }
                String id = pathInfo.substring(1);
                UpdateCategoryRequest req = readJson(request, UpdateCategoryRequest.class);
                CategoryResponse updated = menuService.updateCategory(id, req);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
            } else if (servletPath.startsWith("/api/admin/foods")) {
                if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                    badRequest(response, "Thiếu mã món cần cập nhật.");
                    return;
                }
                String id = pathInfo.substring(1);
                UpdateFoodRequest req = readJson(request, UpdateFoodRequest.class);
                FoodDetailResponse updated = menuService.updateFood(id, req);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
            } else if (servletPath.startsWith("/api/admin/options")) {
                if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                    badRequest(response, "Thiếu mã tùy chọn cần cập nhật.");
                    return;
                }
                String id = pathInfo.substring(1);
                UpdateFoodOptionRequest req = readJson(request, UpdateFoodOptionRequest.class);
                FoodOptionResponse updated = menuService.updateFoodOption(id, req);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
            } else {
                badRequest(response, "Đường dẫn không hợp lệ.");
            }
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();

            if (servletPath.startsWith("/api/admin/categories")) {
                if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                    badRequest(response, "Thiếu mã danh mục cần xóa.");
                    return;
                }
                String id = pathInfo.substring(1);
                menuService.deleteCategory(id);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success("Đã xóa danh mục thành công."));
            } else if (servletPath.startsWith("/api/admin/foods")) {
                if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                    badRequest(response, "Thiếu mã món cần xóa.");
                    return;
                }
                String id = pathInfo.substring(1);
                menuService.deleteFood(id);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success("Đã xóa món ăn thành công."));
            } else if (servletPath.startsWith("/api/admin/options")) {
                if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                    badRequest(response, "Thiếu mã tùy chọn cần xóa.");
                    return;
                }
                String id = pathInfo.substring(1);
                menuService.deleteFoodOption(id);
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success("Đã xóa tùy chọn thành công."));
            } else {
                badRequest(response, "Đường dẫn không hợp lệ.");
            }
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    private void handleGetCategory(HttpServletRequest request, HttpServletResponse response, String pathInfo) throws IOException {
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            List<CategoryResponse> categories = menuService.getCategories();
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(categories));
            return;
        }
        String id = pathInfo.substring(1);
        CategoryResponse category = menuService.getCategoryById(id);
        writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(category));
    }

    private void handleGetFood(HttpServletRequest request, HttpServletResponse response, String pathInfo) throws IOException {
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            String categoryId = request.getParameter("category_id");
            String keyword = request.getParameter("keyword");
            String statusParam = request.getParameter("status");
            FoodStatus status = null;
            if (statusParam != null && !statusParam.trim().isEmpty()) {
                try {
                    status = FoodStatus.valueOf(statusParam.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                }
            }
            List<FoodSummaryResponse> foods = menuService.getAllFoodsAdmin(categoryId, keyword, status);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(foods));
            return;
        }
        String id = pathInfo.substring(1);
        FoodDetailResponse food = menuService.getFoodDetail(id, false);
        writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(food));
    }
}
