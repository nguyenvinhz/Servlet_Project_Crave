package com.foodordering.servlet;

import com.foodordering.bean.FoodDetailBean;
import com.foodordering.bean.FoodOptionBean;
import com.foodordering.bean.ProfileBean;
import com.foodordering.enums.AccountType;
import com.foodordering.exception.AppException;
import com.foodordering.exception.BusinessException;
import com.foodordering.service.MenuService;
import com.foodordering.service.CartService;
import com.foodordering.security.SessionAuth;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet(name = "MenuServlet", urlPatterns = {"/menu", "/menu/detail"})
public class MenuServlet extends HttpServlet {
    private final MenuService menuService;
    private final CartService cartService;

    public MenuServlet() {
        this(new MenuService(), new CartService());
    }

    public MenuServlet(MenuService menuService) {
        this(menuService, new CartService());
    }

    public MenuServlet(MenuService menuService, CartService cartService) {
        this.menuService = menuService;
        this.cartService = cartService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            if (request.getSession(false) != null) {
                var session = request.getSession(false);
                if (session.getAttribute(SessionAuth.CURRENT_USER) instanceof ProfileBean user
                        && user.accountType() == AccountType.CUSTOMER) {
                    int count = cartService.countItems(user.id());
                    if (session.getAttribute(SessionAuth.CURRENT_USER) == user) {
                        session.setAttribute("cartItemCount", count);
                    }
                }
                for (String key : List.of("flashError", "flashMessage")) {
                    Object message = request.getSession(false).getAttribute(key);
                    if (message != null) {
                        request.setAttribute(key, message);
                        request.getSession(false).removeAttribute(key);
                    }
                }
            }
            if ("/menu/detail".equals(request.getServletPath())) {
                String id = request.getParameter("id");
                if (id == null || id.isBlank()) {
                    response.sendRedirect(request.getContextPath() + "/menu");
                    return;
                }
                FoodDetailBean food = menuService.getFoodDetail(id, true);
                request.setAttribute("food", food);
                prepareOptions(request, food, null);
                request.setAttribute("pageTitle", "Chi tiết món ăn");
                request.getRequestDispatcher("/WEB-INF/views/menu/detail.jsp").forward(request, response);
                return;
            }
            String categoryId = request.getParameter("categoryId");
            String keyword = request.getParameter("keyword");
            request.setAttribute("categories", menuService.getCategories());
            request.setAttribute("foods", menuService.getMenu(categoryId, keyword));
            request.setAttribute("currentCategoryId", categoryId);
            request.setAttribute("keyword", keyword);
            request.setAttribute("pageTitle", "Thực đơn");
            request.getRequestDispatcher("/WEB-INF/views/menu/index.jsp").forward(request, response);
        } catch (AppException exception) {
            response.sendError(exception.getStatusCode(), exception.getMessage());
        } catch (BusinessException exception) {
            response.sendError(exception.getHttpStatus(), exception.getMessage());
        }
    }

    static void prepareOptions(HttpServletRequest request, FoodDetailBean food, Set<String> selectedIds) {
        Map<String, List<FoodOptionBean>> groups = new LinkedHashMap<>();
        if (food.options() != null) {
            for (FoodOptionBean option : food.options()) {
                groups.computeIfAbsent(option.optionType().name(), key -> new ArrayList<>()).add(option);
            }
        }
        Set<String> selected = selectedIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(selectedIds);
        if (selectedIds == null) {
            for (String type : List.of("SUGAR_LEVEL", "ICE_LEVEL")) {
                if (groups.containsKey(type) && !groups.get(type).isEmpty()) {
                    selected.add(groups.get(type).get(0).id());
                }
            }
        }
        request.setAttribute("optionGroups", groups);
        request.setAttribute("selectedOptionIds", selected);
    }
}
