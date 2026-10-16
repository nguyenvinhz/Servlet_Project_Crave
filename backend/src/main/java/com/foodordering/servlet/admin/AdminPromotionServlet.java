package com.foodordering.servlet.admin;

import com.foodordering.bean.AdminPromotionForm;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.ValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminPromotionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@WebServlet(name = "AdminPromotionServlet", urlPatterns = "/admin/promotions")
public class AdminPromotionServlet extends HttpServlet {
    private AdminPromotionService promotions;
    public AdminPromotionServlet() { }
    public AdminPromotionServlet(AdminPromotionService promotions) { this.promotions = promotions; }
    @Override public void init() { if (promotions == null) promotions = new AdminPromotionService(); }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        SessionAuth.requireEmployee(request, EmployeeRole.PROMOTION_MANAGER);
        AdminForms.flash(request);
        var all = promotions.listPromotions();
        String keyword = AdminForms.text(request, "keyword").toLowerCase(Locale.ROOT);
        String status = AdminForms.text(request, "status");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        request.setAttribute("promotions", all.stream().filter(p -> (keyword.isEmpty() || (p.getCode() + " " + p.getName()).toLowerCase(Locale.ROOT).contains(keyword))
                && (status.isEmpty() || status.equals(p.getStatus() == PromotionStatus.INACTIVE ? "INACTIVE"
                : now.isBefore(p.getStartAt()) ? "UPCOMING" : now.isAfter(p.getEndAt()) ? "EXPIRED" : "CURRENT"))).toList());
        request.setAttribute("now", now);
        String editId = AdminForms.text(request, "edit");
        if (!editId.isEmpty()) request.setAttribute("editingPromotion", all.stream().filter(p -> p.getPromotionId().equals(editId)).findFirst().orElse(null));
        request.getRequestDispatcher("/WEB-INF/views/admin/promotions.jsp").forward(request, response);
    }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionAuth.requireEmployee(request, EmployeeRole.PROMOTION_MANAGER);
        Exception error = null;
        try {
            String action = AdminForms.text(request, "action"), id = AdminForms.text(request, "id");
            if ("status".equals(action)) promotions.updatePromotionStatus(id, AdminForms.choice(request, "status", PromotionStatus.class));
            else if ("create".equals(action) || "update".equals(action)) {
                LocalDateTime start, end;
                try { start = LocalDateTime.parse(AdminForms.text(request, "startAt")); end = LocalDateTime.parse(AdminForms.text(request, "endAt")); }
                catch (DateTimeParseException exception) { throw new ValidationException("Thời gian khuyến mãi không hợp lệ."); }
                var form = new AdminPromotionForm(request.getParameter("code"), request.getParameter("name"),
                        AdminForms.choice(request, "discountType", DiscountType.class), AdminForms.amount(request, "discountValue", false),
                        AdminForms.amount(request, "minimumOrderValue", false), AdminForms.amount(request, "maximumDiscount", true),
                        start, end, AdminForms.choice(request, "status", PromotionStatus.class));
                if ("create".equals(action)) promotions.createPromotion(form); else promotions.updatePromotion(id, form);
            } else throw new ValidationException("Thao tác khuyến mãi không hợp lệ.");
        } catch (Exception exception) { error = exception; }
        AdminForms.result(request, response, request.getServletPath(), error);
    }
}
