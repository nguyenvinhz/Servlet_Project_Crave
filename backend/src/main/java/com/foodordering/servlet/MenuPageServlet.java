package com.foodordering.servlet;

import com.foodordering.dto.ProfileResponse;
import com.foodordering.enums.AccountType;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.service.CartService;
import com.foodordering.service.MenuService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet(name = "menuPageServlet", urlPatterns = {"/menu", "/menu/detail"})
public class MenuPageServlet extends HttpServlet {
    private MenuService menuService;

    @Override
    public void init() throws ServletException {
        this.menuService = new MenuService();
    }

    public void setMenuService(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getServletPath();
        try {
            if ("/menu/detail".equals(path)) {
                String id = request.getParameter("id");
                if (id == null || id.isBlank()) {
                    response.sendRedirect(request.getContextPath() + "/menu");
                    return;
                }
                request.setAttribute("food", menuService.getFoodDetail(id, true));
                request.setAttribute("pageTitle", "Chi tiết món ăn");
            } else {
                String categoryId = request.getParameter("categoryId");
                String keyword = request.getParameter("keyword");
                request.setAttribute("categories", menuService.getCategories());
                request.setAttribute("foods", menuService.getMenu(categoryId, keyword));
                request.setAttribute("currentCategoryId", categoryId);
                request.setAttribute("keyword", keyword);
                request.setAttribute("pageTitle", "Thực đơn");
            }
        } catch (ResourceNotFoundException exception) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, exception.getMessage());
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("currentUser") instanceof ProfileResponse user
                && user.accountType() == AccountType.CUSTOMER) {
            try {
                session.setAttribute("cartItemCount", new CartService().getOrCreateCart(user.id()).getTotalItems());
            } catch (RuntimeException ignored) {
                // The menu stays available if the cart cannot be loaded.
            }
        }
        request.getRequestDispatcher("/menu/detail".equals(path)
                ? "/WEB-INF/views/menu/detail.jsp" : "/WEB-INF/views/menu/index.jsp").forward(request, response);
    }
}