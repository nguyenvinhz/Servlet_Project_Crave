package com.foodordering.servlet;

import com.foodordering.dto.AdminPromotionRequest;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.AppException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminPromotionService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@WebServlet(name = "AdminPromotionServlet", urlPatterns = "/admin/promotions/action")
public class AdminPromotionServlet extends BaseServlet {
    private final AdminPromotionService promotions;
    public AdminPromotionServlet() { this(new AdminPromotionService()); }
    public AdminPromotionServlet(AdminPromotionService promotions) { this.promotions = promotions; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            SessionAuth.requireEmployee(request, EmployeeRole.PROMOTION_MANAGER);
            String action = request.getParameter("action"), id = request.getParameter("id");
            if ("status".equals(action)) {
                promotions.updatePromotionStatus(id, PromotionStatus.valueOf(request.getParameter("status")));
            } else if ("create".equals(action) || "update".equals(action)) {
                AdminPromotionRequest form = new AdminPromotionRequest(request.getParameter("code"), request.getParameter("name"),
                        DiscountType.valueOf(request.getParameter("discountType")), decimal(request, "discountValue"),
                        decimal(request, "minimumOrderValue"), decimal(request, "maximumDiscount"),
                        LocalDateTime.parse(request.getParameter("startAt")), LocalDateTime.parse(request.getParameter("endAt")),
                        PromotionStatus.valueOf(request.getParameter("status")));
                if ("create".equals(action)) promotions.createPromotion(form);
                else promotions.updatePromotion(id, form);
            } else { response.sendError(400, "Thao tác khuyến mãi không hợp lệ."); return; }
            request.getSession().setAttribute("adminSuccess", "Đã lưu chương trình khuyến mãi.");
        } catch (AppException exception) {
            if (exception.getStatusCode() == 401 || exception.getStatusCode() == 403) { handleError(response, exception); return; }
            request.getSession().setAttribute("adminError", exception.getMessage());
        } catch (IllegalArgumentException | NullPointerException | java.time.format.DateTimeParseException exception) {
            request.getSession().setAttribute("adminError", "Vui lòng kiểm tra thông tin và thời gian khuyến mãi.");
        } catch (Exception exception) { handleError(response, exception); return; }
        response.sendRedirect(request.getContextPath() + "/admin/promotions");
    }

    private static BigDecimal decimal(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }
}
