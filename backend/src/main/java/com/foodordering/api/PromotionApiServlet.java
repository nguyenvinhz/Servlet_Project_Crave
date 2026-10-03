package com.foodordering.api;

import com.foodordering.dto.ApiResponse;
import com.foodordering.dto.PromotionDto;
import com.foodordering.dto.PromotionValidationResultDto;
import com.foodordering.enums.ErrorCode;
import com.foodordering.exception.ApiException;
import com.foodordering.service.PromotionService;
import com.foodordering.utils.JsonUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Servlet cung cấp RESTful API cho khuyến mãi và kiểm tra voucher (Promotion API do Ung Văn Trí phụ trách).
 * Route: /api/promotions, /api/promotions/*
 */
@WebServlet(name = "PromotionApiServlet", urlPatterns = {"/api/promotions", "/api/promotions/*"})
public class PromotionApiServlet extends HttpServlet {

    private final PromotionService promotionService = new PromotionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setupResponseHeaders(resp);
        String pathInfo = req.getPathInfo();
        String code = req.getParameter("code");

        try {
            if (code != null && !code.trim().isEmpty()) {
                // Tra cứu chi tiết một mã
                PromotionDto promo = promotionService.getPromotionByCode(code.trim());
                if (promo == null) {
                    writeResponse(resp, HttpServletResponse.SC_NOT_FOUND,
                            ApiResponse.error(ErrorCode.PROMOTION_NOT_FOUND, "Không tìm thấy mã khuyến mãi"));
                } else {
                    writeResponse(resp, HttpServletResponse.SC_OK,
                            ApiResponse.success("Lấy thông tin khuyến mãi thành công", promo));
                }
            } else {
                // Mặc định hoặc /active: danh sách khuyến mãi đang hoạt động
                List<PromotionDto> activeList = promotionService.getActivePromotions();
                writeResponse(resp, HttpServletResponse.SC_OK,
                        ApiResponse.success("Lấy danh sách mã khuyến mãi thành công", activeList));
            }
        } catch (ApiException e) {
            writeResponse(resp, e.getHttpStatus(), ApiResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            writeResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi máy chủ: " + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setupResponseHeaders(resp);
        String pathInfo = req.getPathInfo();
        String customerId = resolveCustomerId(req);

        String body = readRequestBody(req);
        Map<String, Object> bodyMap = JsonUtils.parseJsonObject(body);

        String code = JsonUtils.getString(bodyMap, "code");
        if (code == null) code = req.getParameter("code");

        BigDecimal subtotal = JsonUtils.getBigDecimal(bodyMap, "subtotal");
        if (subtotal == null && req.getParameter("subtotal") != null) {
            try {
                subtotal = new BigDecimal(req.getParameter("subtotal").trim());
            } catch (Exception ignored) {
            }
        }

        try {
            // Xác thực mã khuyến mãi
            PromotionValidationResultDto result = promotionService.validatePromotion(
                    code, customerId, subtotal, LocalDateTime.now());

            if (result.isValid()) {
                writeResponse(resp, HttpServletResponse.SC_OK,
                        ApiResponse.success("Mã khuyến mãi hợp lệ", result));
            } else {
                writeResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
                        ApiResponse.error(result.getErrorCode() != null ? result.getErrorCode() : ErrorCode.BAD_REQUEST.getCode(),
                                result.getMessage()));
            }
        } catch (ApiException e) {
            writeResponse(resp, e.getHttpStatus(), ApiResponse.error(e.getErrorCode(), e.getMessage()));
        } catch (Exception e) {
            writeResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi máy chủ: " + e.getMessage()));
        }
    }

    private String resolveCustomerId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            Object cid = session.getAttribute("customerId");
            if (cid != null) return cid.toString().trim();
            Object userObj = session.getAttribute("user");
            if (userObj != null) {
                try {
                    var m = userObj.getClass().getMethod("getCustomerId");
                    Object res = m.invoke(userObj);
                    if (res != null) return res.toString().trim();
                } catch (Exception ignored) {
                }
            }
        }
        String headerCid = req.getHeader("X-Customer-Id");
        if (headerCid != null && !headerCid.trim().isEmpty()) {
            return headerCid.trim();
        }
        String paramCid = req.getParameter("customerId");
        if (paramCid != null && !paramCid.trim().isEmpty()) {
            return paramCid.trim();
        }
        return null;
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

    private void setupResponseHeaders(HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Customer-Id");
    }

    private void writeResponse(HttpServletResponse resp, int statusCode, ApiResponse<?> apiResponse) throws IOException {
        resp.setStatus(statusCode);
        resp.getWriter().write(JsonUtils.toJson(apiResponse));
        resp.getWriter().flush();
    }
}
