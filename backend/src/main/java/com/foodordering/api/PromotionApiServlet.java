package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.PromotionDto;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.enums.ErrorCode;
import com.foodordering.service.PromotionService;
import com.foodordering.utils.JsonUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@WebServlet(name = "PromotionApiServlet", urlPatterns = {"/api/promotions", "/api/promotions/*"})
public class PromotionApiServlet extends HttpServlet {

    private final PromotionService promotionService = new PromotionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String code = req.getParameter("code");
        if (code != null) {
            PromotionDto promo = promotionService.getPromotionByCode(code);
            if (promo == null) {
                writeResponse(resp, HttpServletResponse.SC_NOT_FOUND, new ApiResponse(false, "Không tìm thấy mã khuyến mãi", null, ErrorCode.PROMOTION_NOT_FOUND));
                return;
            }
            writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse(true, "Lấy thông tin khuyến mãi thành công", promo, null));
            return;
        }
        List<PromotionDto> activeList = promotionService.getActivePromotions();
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse(true, "Lấy danh sách mã khuyến mãi thành công", activeList, null));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession session = req.getSession();
        String customerId = (String) session.getAttribute("customerId");
        ValidatePromotionRequest requestDto = readValidatePromotionDto(req);
        PromotionValidationResultDto result = promotionService.validatePromotion(requestDto, customerId);
        if (!result.isValid()) {
            writeResponse(resp, HttpServletResponse.SC_BAD_REQUEST, new ApiResponse(false, result.getMessage(), null, result.getErrorCode()));
            return;
        }
        writeResponse(resp, HttpServletResponse.SC_OK, new ApiResponse(true, "Áp dụng mã khuyến mãi thành công", result, null));
    }

    private ValidatePromotionRequest readValidatePromotionDto(HttpServletRequest req) throws IOException {
        String body = readRequestBody(req);
        ValidatePromotionRequest dto = JsonUtils.toDTO(body, ValidatePromotionRequest.class);
        return dto;
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void writeResponse(HttpServletResponse resp, int statusCode, ApiResponse<?> apiResponse) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setStatus(statusCode);
        resp.getWriter().write(JsonUtils.toJson(apiResponse));
        resp.getWriter().flush();
    }
}
