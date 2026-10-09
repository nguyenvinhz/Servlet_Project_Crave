package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.CategoryResponse;
import com.foodordering.service.MenuService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(name = "categoryApiServlet", urlPatterns = {"/api/categories", "/api/categories/*"})
public class CategoryApiServlet extends BaseApiServlet {

    private final MenuService menuService;

    public CategoryApiServlet() {
        this(new MenuService());
    }

    public CategoryApiServlet(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
                List<CategoryResponse> categories = menuService.getCategories();
                writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(categories));
                return;
            }

            String id = pathInfo.substring(1);
            CategoryResponse category = menuService.getCategoryById(id);
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(category));
        } catch (Exception e) {
            handleError(response, e);
        }
    }
}
