package com.foodordering.api;

import com.foodordering.dto.AccountStatusUpdateRequest;
import com.foodordering.dto.AdminPromotionRequest;
import com.foodordering.dto.ApiResponse;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.ValidationException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AdminPromotionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@WebServlet(name = "AdminPromotionApiServlet", urlPatterns = {"/api/admin/promotions", "/api/admin/promotions/*"})
public class AdminPromotionApiServlet extends BaseApiServlet {
    private final AdminPromotionService promotions;

    public AdminPromotionApiServlet() {
        this(new AdminPromotionService());
    }

    public AdminPromotionApiServlet(AdminPromotionService promotions) {
        this.promotions = Objects.requireNonNull(promotions);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(request.getMethod())) doPatch(request, response);
        else super.service(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            requireManager(request);
            if (!root(request)) {
                badRequest(response, "Đường dẫn danh sách khuyến mãi không hợp lệ.");
                return;
            }
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(promotions.listPromotions()));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            requireManager(request);
            if (!root(request)) {
                badRequest(response, "Chỉ hỗ trợ tạo khuyến mãi tại /api/admin/promotions.");
                return;
            }
            AdminPromotionRequest body = readJson(request, AdminPromotionRequest.class);
            writeJson(response, HttpServletResponse.SC_CREATED, ApiResponse.success(promotions.createPromotion(body)));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            requireManager(request);
            String path = request.getPathInfo();
            if (path == null || !path.matches("/[^/]+(/status)?")) {
                badRequest(response, "Đường dẫn phải có dạng /{id} hoặc /{id}/status.");
                return;
            }
            String id = path.substring(1).split("/")[0];
            Object result;
            if (path.endsWith("/status")) {
                AccountStatusUpdateRequest body = readJson(request, AccountStatusUpdateRequest.class);
                if (body == null || body.status() == null || body.status().isBlank()) {
                    throw new ValidationException("Vui lòng chọn trạng thái khuyến mãi.");
                }
                result = promotions.updatePromotionStatus(id, PromotionStatus.valueOf(body.status().strip().toUpperCase(Locale.ROOT)));
            } else {
                result = promotions.updatePromotion(id, readJson(request, AdminPromotionRequest.class));
            }
            writeJson(response, HttpServletResponse.SC_OK, ApiResponse.success(result));
        } catch (Exception exception) {
            handleError(response, exception);
        }
    }

    private static void requireManager(HttpServletRequest request) {
        SessionAuth.requireEmployee(request, EmployeeRole.PROMOTION_MANAGER);
    }

    private static boolean root(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null || path.isEmpty() || "/".equals(path);
    }
}
