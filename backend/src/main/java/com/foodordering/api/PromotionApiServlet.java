package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.PromotionDto;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.PromotionService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet(name = "PromotionApiServlet", urlPatterns = {"/api/promotions", "/api/promotions/*"})
public class PromotionApiServlet extends BaseApiServlet {

    private final PromotionService promotionService;

    public PromotionApiServlet() {
        this(new PromotionService());
    }

    public PromotionApiServlet(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String code = req.getParameter("code");
            if (code != null) {
                PromotionDto promo = promotionService.getPromotionByCode(code);
                if (promo == null) {
                    throw new ResourceNotFoundException(ErrorCode.PROMOTION_NOT_FOUND, "Không tìm thấy mã khuyến mãi");
                }
                writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success(promo));
                return;
            }
            List<PromotionDto> activeList = promotionService.getActivePromotions();
            writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success(activeList));
        } catch (Exception exception) {
            handleError(resp, exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(req).id();
            ValidatePromotionRequest requestDto = readJson(req, ValidatePromotionRequest.class);
            PromotionValidationResultDto result = promotionService.validatePromotion(requestDto, customerId);
            if (!result.isValid()) {
                writeJson(resp, HttpServletResponse.SC_BAD_REQUEST, ApiResponse.error(result.getMessage(), result.getErrorCode()));
                return;
            }
            writeJson(resp, HttpServletResponse.SC_OK, ApiResponse.success(result));
        } catch (Exception exception) {
            handleError(resp, exception);
        }
    }
}
