package com.foodordering.api;

import com.foodordering.dto.ApiError;
import com.foodordering.dto.ApiResponse;
import com.foodordering.utils.JsonProvider;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

public abstract class BaseApiServlet extends HttpServlet {

    protected void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        JsonProvider.objectMapper().writeValue(response.getWriter(), body);
    }

    protected void notImplemented(HttpServletResponse response, String operation) throws IOException {
        writeJson(response, HttpServletResponse.SC_NOT_IMPLEMENTED,
                ApiResponse.failure(new ApiError(
                        "NOT_IMPLEMENTED",
                        operation + " is contracted but will be implemented on Day 2.",
                        Map.of())));
    }

    protected void methodNotAllowed(HttpServletResponse response, String allowedMethods) throws IOException {
        response.setHeader("Allow", allowedMethods);
        writeJson(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                ApiResponse.failure(new ApiError(
                        "METHOD_NOT_ALLOWED",
                        "Allowed methods: " + allowedMethods,
                        Map.of())));
    }

    protected void badRequest(HttpServletResponse response, String message) throws IOException {
        writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                ApiResponse.failure(new ApiError("INVALID_REQUEST", message, Map.of())));
    }

    protected <T> T readJson(jakarta.servlet.http.HttpServletRequest request, Class<T> clazz) throws IOException {
        return JsonProvider.objectMapper().readValue(request.getInputStream(), clazz);
    }

    protected void handleError(HttpServletResponse response, Exception e) throws IOException {
        if (e instanceof com.foodordering.exception.AppException appEx) {
            writeJson(response, appEx.getStatusCode(),
                    ApiResponse.failure(new ApiError(appEx.getErrorCode(), appEx.getMessage(), appEx.getDetails())));
        } else {
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    ApiResponse.failure(new ApiError("INTERNAL_SERVER_ERROR", e.getMessage() != null ? e.getMessage() : "Đã xảy ra lỗi hệ thống.", Map.of())));
        }
    }
}
