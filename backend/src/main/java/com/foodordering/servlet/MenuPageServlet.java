package com.foodordering.servlet;

import com.foodordering.enums.FoodStatus;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.service.MenuService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "menuPageServlet", urlPatterns = {
        "/menu",
        "/menu/detail",
        "/admin/menu",
        "/admin/categories"
})
public class MenuPageServlet extends HttpServlet {

    private MenuService menuService;

    @Override
    public void init() throws ServletException {
        super.init();
        this.menuService = new MenuService();
    }

    public void setMenuService(MenuService menuService) {
        this.menuService = menuService;
    }

    private static final Map<String, Page> PAGES = Map.of(
            "/menu", new Page("Thực đơn", "/WEB-INF/views/menu/index.jsp"),
            "/menu/detail", new Page("Chi tiết món ăn", "/WEB-INF/views/menu/detail.jsp"),
            "/admin/menu", new Page("Quản lý thực đơn", "/WEB-INF/views/admin/menu.jsp"),
            "/admin/categories", new Page("Quản lý danh mục", "/WEB-INF/views/admin/categories.jsp")
    );

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        Page page = PAGES.get(path);
        if (page == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            if ("/menu".equals(path)) {
                String categoryId = request.getParameter("categoryId");
                String keyword = request.getParameter("keyword");
                request.setAttribute("categories", menuService.getCategories());
                request.setAttribute("foods", menuService.getMenu(categoryId, keyword));
                request.setAttribute("currentCategoryId", categoryId);
                request.setAttribute("keyword", keyword);
            } else if ("/menu/detail".equals(path)) {
                String id = request.getParameter("id");
                if (id != null && !id.isBlank()) {
                    request.setAttribute("food", menuService.getFoodDetail(id, true));
                } else {
                    response.sendRedirect(request.getContextPath() + "/menu");
                    return;
                }
            } else if ("/admin/menu".equals(path)) {
                String categoryId = request.getParameter("categoryId");
                String keyword = request.getParameter("keyword");
                String statusParam = request.getParameter("status");
                FoodStatus status = null;
                if (statusParam != null && !statusParam.isBlank()) {
                    try {
                        status = FoodStatus.valueOf(statusParam.trim().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                request.setAttribute("categories", menuService.getCategories());
                request.setAttribute("foods", menuService.getAllFoodsForAdmin(categoryId, keyword, status));
                request.setAttribute("currentCategoryId", categoryId);
                request.setAttribute("keyword", keyword);
                request.setAttribute("currentStatus", statusParam);
            } else if ("/admin/categories".equals(path)) {
                request.setAttribute("categories", menuService.getCategories());
            }
        } catch (ResourceNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            return;
        }

        request.setAttribute("pageTitle", page.title());
        request.getRequestDispatcher(page.jsp()).forward(request, response);
    }

    private record Page(String title, String jsp) {
    }
}
