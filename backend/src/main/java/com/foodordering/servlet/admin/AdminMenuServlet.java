package com.foodordering.servlet.admin;

import com.foodordering.bean.CategoryForm;
import com.foodordering.bean.FoodOptionForm;
import com.foodordering.bean.FoodForm;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.exception.AppException;
import com.foodordering.exception.ValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.MenuService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@WebServlet(name = "AdminMenuServlet", urlPatterns = {"/admin/menu", "/admin/categories"})
public class AdminMenuServlet extends HttpServlet {
    private MenuService menu;
    public AdminMenuServlet() { }
    public AdminMenuServlet(MenuService menu) { this.menu = menu; }
    @Override public void init() { if (menu == null) menu = new MenuService(); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        SessionAuth.requireEmployee(request, EmployeeRole.MENU_MANAGER);
        AdminForms.flash(request);
        boolean categories = "/admin/categories".equals(request.getServletPath());
        try {
            request.setAttribute("categories", menu.getCategories());
            String editId = AdminForms.text(request, "edit");
            if (categories) {
                if (!editId.isEmpty()) request.setAttribute("editingCategory", menu.getCategoryById(editId));
            } else {
                String status = AdminForms.text(request, "status");
                request.setAttribute("foods", menu.getAllFoodsForAdmin(request.getParameter("categoryId"),
                        request.getParameter("keyword"), status.isEmpty() ? null : AdminForms.choice(request, "status", FoodStatus.class)));
                if (!editId.isEmpty()) request.setAttribute("editingFood", menu.getFoodDetail(editId, false));
                String foodId = AdminForms.text(request, "foodId");
                if (!foodId.isEmpty()) {
                    request.setAttribute("optionFood", menu.getFoodDetail(foodId, false));
                    String optionId = AdminForms.text(request, "optionId");
                    if (!optionId.isEmpty()) {
                        var option = menu.getFoodOptionById(optionId);
                        if (!foodId.equals(option.foodId())) throw new ValidationException("Tùy chọn không thuộc món đã chọn.");
                        request.setAttribute("editingOption", option);
                    }
                }
                request.setAttribute("optionTypes", OptionType.values());
            }
        } catch (AppException exception) { response.sendError(exception.getStatusCode(), exception.getMessage()); return; }
        request.getRequestDispatcher("/WEB-INF/views/admin/" + (categories ? "categories" : "menu") + ".jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionAuth.requireEmployee(request, EmployeeRole.MENU_MANAGER);
        String destination = request.getServletPath();
        Exception error = null;
        try {
            String action = AdminForms.text(request, "action");
            String id = AdminForms.text(request, "id");
            if ("/admin/categories".equals(destination)) {
                var form = new CategoryForm(request.getParameter("name"), request.getParameter("description"));
                switch (action) {
                    case "create" -> menu.createCategory(form);
                    case "update" -> menu.updateCategory(id, form);
                    case "delete" -> menu.deleteCategory(id);
                    default -> throw new ValidationException("Thao tác danh mục không hợp lệ.");
                }
            } else if (action.startsWith("option")) {
                String foodId = AdminForms.text(request, "foodId");
                destination += "?foodId=" + URLEncoder.encode(foodId, StandardCharsets.UTF_8);
                if (!"optionCreate".equals(action) && !foodId.equals(menu.getFoodOptionById(id).foodId())) {
                    throw new ValidationException("Tùy chọn không thuộc món đã chọn.");
                }
                switch (action) {
                    case "optionCreate", "optionUpdate" -> {
                        var form = new FoodOptionForm(foodId, AdminForms.choice(request, "optionType", OptionType.class),
                                request.getParameter("name"), AdminForms.amount(request, "extraPrice", false),
                                AdminForms.choice(request, "status", OptionStatus.class));
                        if ("optionCreate".equals(action)) menu.createFoodOption(foodId, form);
                        else menu.updateFoodOption(id, form);
                    }
                    case "optionStatus" -> menu.updateFoodOptionStatus(id, AdminForms.choice(request, "status", OptionStatus.class));
                    case "optionDelete" -> menu.deleteFoodOption(id);
                    default -> throw new ValidationException("Thao tác tùy chọn không hợp lệ.");
                }
            } else {
                switch (action) {
                    case "create", "update" -> {
                        var form = new FoodForm(request.getParameter("categoryId"), request.getParameter("name"),
                                AdminForms.amount(request, "price", false), request.getParameter("imageUrl"),
                                request.getParameter("description"), AdminForms.choice(request, "status", FoodStatus.class));
                        if ("create".equals(action)) menu.createFood(form); else menu.updateFood(id, form);
                    }
                    case "status" -> menu.updateFoodStatus(id, AdminForms.choice(request, "status", FoodStatus.class));
                    case "delete" -> menu.deleteFood(id);
                    default -> throw new ValidationException("Thao tác món ăn không hợp lệ.");
                }
            }
        } catch (Exception exception) { error = exception; }
        AdminForms.result(request, response, destination, error);
    }
}
