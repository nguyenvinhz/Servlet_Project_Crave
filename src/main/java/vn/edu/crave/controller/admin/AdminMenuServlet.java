package vn.edu.crave.controller.admin;

import vn.edu.crave.controller.BaseServlet;

import vn.edu.crave.model.form.CategoryRequest;
import vn.edu.crave.model.form.FoodRequest;
import vn.edu.crave.model.form.FoodOptionRequest;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.FoodStatus;
import vn.edu.crave.model.enums.OptionStatus;
import vn.edu.crave.model.enums.OptionType;
import vn.edu.crave.exception.AppException;
import vn.edu.crave.exception.ApiException;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.MenuService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet(name = "AdminMenuServlet", urlPatterns = {"/admin/menu", "/admin/categories", "/admin/menu/action", "/admin/categories/action"})
public class AdminMenuServlet extends BaseServlet {
    private final MenuService menu;
    public AdminMenuServlet() { this(new MenuService()); }
    public AdminMenuServlet(MenuService menu) { this.menu = menu; }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.MENU_MANAGER);
            request.setAttribute("categories", menu.getCategories());
            boolean categories = "/admin/categories".equals(request.getServletPath());
            if (!categories) {
                String category = request.getParameter("categoryId"), keyword = request.getParameter("keyword");
                String status = request.getParameter("status");
                request.setAttribute("foods", menu.getAllFoodsForAdmin(category, keyword,
                        status == null || status.isBlank() ? null : FoodStatus.valueOf(status)));
                request.setAttribute("currentCategoryId", category);
                request.setAttribute("keyword", keyword);
                request.setAttribute("currentStatus", status);
                String editId = request.getParameter("edit"), optionsId = request.getParameter("options");
                if (editId != null && !editId.isBlank()) request.setAttribute("editingFood", menu.getFoodDetail(editId, false));
                if (optionsId != null && !optionsId.isBlank()) {
                    request.setAttribute("optionsFood", menu.getFoodDetail(optionsId, false));
                    request.setAttribute("foodOptions", menu.getOptionsByFoodId(optionsId, false));
                }
            }
            request.getRequestDispatcher("/WEB-INF/views/admin/" + (categories ? "categories" : "menu") + ".jsp").forward(request, response);
        } catch (Exception exception) { handleError(response, exception); }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String page = request.getServletPath().contains("categories") ? "/admin/categories" : "/admin/menu";
        String foodId = request.getParameter("foodId"), action = request.getParameter("action"), id = request.getParameter("id");
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.MENU_MANAGER);
            if ("/admin/categories".equals(page)) {
                if ("delete".equals(action)) menu.deleteCategory(id);
                else if ("create".equals(action) || "update".equals(action)) {
                    CategoryRequest form = new CategoryRequest(request.getParameter("name"), request.getParameter("description"));
                    if ("create".equals(action)) menu.createCategory(form); else menu.updateCategory(id, form);
                } else { response.sendError(400, "Thao tác danh mục không hợp lệ."); return; }
            } else {
                switch (action == null ? "" : action) {
                    case "create", "update" -> {
                        FoodRequest form = new FoodRequest(request.getParameter("categoryId"), request.getParameter("name"),
                                new BigDecimal(request.getParameter("price")), request.getParameter("imageUrl"),
                                request.getParameter("description"), FoodStatus.valueOf(request.getParameter("status")));
                        if ("create".equals(action)) menu.createFood(form); else menu.updateFood(id, form);
                    }
                    case "delete" -> menu.deleteFood(id);
                    case "status" -> menu.updateFoodStatus(id, FoodStatus.valueOf(request.getParameter("status")));
                    case "optionCreate", "optionUpdate" -> {
                        FoodOptionRequest form = new FoodOptionRequest(foodId, OptionType.valueOf(request.getParameter("optionType")),
                                request.getParameter("name"), new BigDecimal(request.getParameter("extraPrice")),
                                OptionStatus.valueOf(request.getParameter("status")));
                        if ("optionCreate".equals(action)) menu.createFoodOption(foodId, form); else menu.updateFoodOption(id, form);
                    }
                    case "optionDelete" -> menu.deleteFoodOption(id);
                    case "optionStatus" -> menu.updateFoodOptionStatus(id, OptionStatus.valueOf(request.getParameter("status")));
                    default -> { response.sendError(400, "Thao tác món ăn không hợp lệ."); return; }
                }
            }
            request.getSession().setAttribute("adminSuccess", "Đã lưu thay đổi thực đơn.");
        } catch (AppException exception) {
            if (exception.getStatusCode() == 401 || exception.getStatusCode() == 403) { handleError(response, exception); return; }
            request.getSession().setAttribute("adminError", exception.getMessage());
        } catch (ApiException exception) {
            request.getSession().setAttribute("adminError", exception.getMessage());
        } catch (IllegalArgumentException | NullPointerException exception) {
            request.getSession().setAttribute("adminError", "Vui lòng kiểm tra thông tin món ăn và danh mục.");
        } catch (Exception exception) { handleError(response, exception); return; }
        if (action != null && action.startsWith("option") && foodId != null) {
            page += "?options=" + URLEncoder.encode(foodId, StandardCharsets.UTF_8);
        }
        response.sendRedirect(request.getContextPath() + page);
    }
}
