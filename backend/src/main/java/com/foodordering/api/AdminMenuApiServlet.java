package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.CategoryRequest;
import com.foodordering.dto.CategoryResponse;
import com.foodordering.dto.FoodDetailResponse;
import com.foodordering.dto.FoodOptionRequest;
import com.foodordering.dto.FoodOptionResponse;
import com.foodordering.dto.FoodRequest;
import com.foodordering.dto.FoodStatusUpdateRequest;
import com.foodordering.dto.FoodSummaryResponse;
import com.foodordering.entity.Customer;
import com.foodordering.entity.Employee;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.FoodStatus;
import com.foodordering.exception.ForbiddenException;
import com.foodordering.service.MenuService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Endpoint quản trị thực đơn, danh mục và tùy chọn món (Ngày 2).
 */
@WebServlet(name = "adminMenuApiServlet", urlPatterns = {
        "/api/admin/categories",
        "/api/admin/categories/*",
        "/api/admin/foods",
        "/api/admin/foods/*",
        "/api/admin/options",
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

    private void checkAdminAccess(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userObj = session.getAttribute("currentUser");
            if (userObj instanceof Employee employee) {
                EmployeeRole role = employee.getRole();
                if (role != EmployeeRole.ADMIN && role != EmployeeRole.MENU_MANAGER) {
                    throw new ForbiddenException("Bạn không có quyền thực hiện thao tác quản lý menu.");
                }
            } else if (userObj instanceof Customer) {
                throw new ForbiddenException("Tài khoản khách hàng không thể thực hiện thao tác quản trị.");
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            checkAdminAccess(request);
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();
            String[] segments = parseSegments(pathInfo);

            if ("/api/admin/categories".equals(servletPath)) {
                if (segments.length == 0) {
                    List<CategoryResponse> categories = menuService.getCategories();
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(categories));
                } else {
                    String categoryId = segments[0];
                    CategoryResponse category = menuService.getCategoryById(categoryId);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(category));
                }
                return;
            }

            if ("/api/admin/foods".equals(servletPath)) {
                if (segments.length == 0) {
                    String categoryId = request.getParameter("category_id");
                    if (categoryId == null) {
                        categoryId = request.getParameter("categoryId");
                    }
                    String keyword = request.getParameter("keyword");
                    String statusParam = request.getParameter("status");
                    FoodStatus status = null;
                    if (statusParam != null && !statusParam.isBlank()) {
                        try {
                            status = FoodStatus.valueOf(statusParam.trim().toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    List<FoodSummaryResponse> foods = menuService.getAllFoodsForAdmin(categoryId, keyword, status);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(foods));
                } else if (segments.length == 1) {
                    String foodId = segments[0];
                    FoodDetailResponse food = menuService.getFoodDetail(foodId, false);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(food));
                } else if (segments.length == 2 && "options".equalsIgnoreCase(segments[1])) {
                    String foodId = segments[0];
                    List<FoodOptionResponse> options = menuService.getOptionsByFoodId(foodId, false);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(options));
                } else {
                    badRequest(response, "Đường dẫn không hợp lệ: " + pathInfo);
                }
                return;
            }

            if ("/api/admin/options".equals(servletPath)) {
                if (segments.length == 1) {
                    String optionId = segments[0];
                    FoodOptionResponse option = menuService.getFoodOptionById(optionId);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(option));
                } else {
                    badRequest(response, "Cần cung cấp mã tùy chọn: /api/admin/options/{id}");
                }
                return;
            }

            badRequest(response, "Tài nguyên không hợp lệ: " + servletPath);
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            checkAdminAccess(request);
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();
            String[] segments = parseSegments(pathInfo);

            if ("/api/admin/categories".equals(servletPath)) {
                CategoryRequest categoryRequest = readJson(request, CategoryRequest.class);
                CategoryResponse created = menuService.createCategory(categoryRequest);
                writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
                return;
            }

            if ("/api/admin/foods".equals(servletPath)) {
                if (segments.length == 0) {
                    FoodRequest foodRequest = readJson(request, FoodRequest.class);
                    FoodDetailResponse created = menuService.createFood(foodRequest);
                    writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
                } else if (segments.length == 2 && "options".equalsIgnoreCase(segments[1])) {
                    String foodId = segments[0];
                    FoodOptionRequest optionRequest = readJson(request, FoodOptionRequest.class);
                    FoodOptionResponse created = menuService.createFoodOption(foodId, optionRequest);
                    writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
                } else {
                    badRequest(response, "Đường dẫn POST không hợp lệ: " + pathInfo);
                }
                return;
            }

            if ("/api/admin/options".equals(servletPath)) {
                if (segments.length == 1) {
                    String foodId = segments[0];
                    FoodOptionRequest optionRequest = readJson(request, FoodOptionRequest.class);
                    FoodOptionResponse created = menuService.createFoodOption(foodId, optionRequest);
                    writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
                } else {
                    FoodOptionRequest optionRequest = readJson(request, FoodOptionRequest.class);
                    if (optionRequest.foodId() == null || optionRequest.foodId().isBlank()) {
                        badRequest(response, "foodId không được để trống khi tạo tùy chọn.");
                        return;
                    }
                    FoodOptionResponse created = menuService.createFoodOption(optionRequest.foodId(), optionRequest);
                    writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(created));
                }
                return;
            }

            badRequest(response, "Tài nguyên không hợp lệ: " + servletPath);
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            checkAdminAccess(request);
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();
            String[] segments = parseSegments(pathInfo);

            if ("/api/admin/categories".equals(servletPath)) {
                if (segments.length == 1) {
                    String categoryId = segments[0];
                    CategoryRequest categoryRequest = readJson(request, CategoryRequest.class);
                    CategoryResponse updated = menuService.updateCategory(categoryId, categoryRequest);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
                } else {
                    badRequest(response, "Cần cung cấp mã danh mục: /api/admin/categories/{id}");
                }
                return;
            }

            if ("/api/admin/foods".equals(servletPath)) {
                if (segments.length == 1) {
                    String foodId = segments[0];
                    FoodRequest foodRequest = readJson(request, FoodRequest.class);
                    FoodDetailResponse updated = menuService.updateFood(foodId, foodRequest);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
                } else if (segments.length == 2 && "status".equalsIgnoreCase(segments[1])) {
                    String foodId = segments[0];
                    FoodStatusUpdateRequest statusRequest = readJson(request, FoodStatusUpdateRequest.class);
                    FoodDetailResponse updated = menuService.updateFoodStatus(foodId, statusRequest.status());
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
                } else {
                    badRequest(response, "Đường dẫn PUT món ăn không hợp lệ: " + pathInfo);
                }
                return;
            }

            if ("/api/admin/options".equals(servletPath)) {
                if (segments.length == 1) {
                    String optionId = segments[0];
                    FoodOptionRequest optionRequest = readJson(request, FoodOptionRequest.class);
                    FoodOptionResponse updated = menuService.updateFoodOption(optionId, optionRequest);
                    writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(updated));
                } else {
                    badRequest(response, "Cần cung cấp mã tùy chọn: /api/admin/options/{id}");
                }
                return;
            }

            badRequest(response, "Tài nguyên không hợp lệ: " + servletPath);
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            checkAdminAccess(request);
            String servletPath = request.getServletPath();
            String pathInfo = request.getPathInfo();
            String[] segments = parseSegments(pathInfo);

            if ("/api/admin/categories".equals(servletPath)) {
                if (segments.length == 1) {
                    String categoryId = segments[0];
                    menuService.deleteCategory(categoryId);
                    writeJson(response, HttpServletResponse.SC_OK,
                            ApiResponse.success(Map.of("message", "Đã xóa danh mục thành công.", "id", categoryId)));
                } else {
                    badRequest(response, "Cần cung cấp mã danh mục: /api/admin/categories/{id}");
                }
                return;
            }

            if ("/api/admin/foods".equals(servletPath)) {
                if (segments.length == 1) {
                    String foodId = segments[0];
                    menuService.deleteFood(foodId);
                    writeJson(response, HttpServletResponse.SC_OK,
                            ApiResponse.success(Map.of("message", "Đã xóa món ăn thành công.", "id", foodId)));
                } else {
                    badRequest(response, "Cần cung cấp mã món ăn: /api/admin/foods/{id}");
                }
                return;
            }

            if ("/api/admin/options".equals(servletPath)) {
                if (segments.length == 1) {
                    String optionId = segments[0];
                    menuService.deleteFoodOption(optionId);
                    writeJson(response, HttpServletResponse.SC_OK,
                            ApiResponse.success(Map.of("message", "Đã xóa tùy chọn thành công.", "id", optionId)));
                } else {
                    badRequest(response, "Cần cung cấp mã tùy chọn: /api/admin/options/{id}");
                }
                return;
            }

            badRequest(response, "Tài nguyên không hợp lệ: " + servletPath);
        } catch (Exception e) {
            handleError(response, e);
        }
    }

    private String[] parseSegments(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return new String[0];
        }
        String cleaned = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) {
            return new String[0];
        }
        return cleaned.split("/");
    }
}
