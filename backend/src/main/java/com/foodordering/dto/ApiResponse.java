package com.foodordering.dto;

import com.foodordering.enums.ErrorCode;

import java.util.Map;

public record ApiResponse<T>(boolean success, T data, ApiError error) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message, Object errorCode) {
        String code = errorCode instanceof ErrorCode ? ((ErrorCode) errorCode).getCode() : (errorCode != null ? errorCode.toString() : "ERROR");
        return new ApiResponse<>(false, null, new ApiError(code, message, Map.of()));
    }

    public static ApiResponse<Void> failure(ApiError error) {
        return new ApiResponse<>(false, (Void) null, error);
    }

    public String message() {
        return error != null ? error.message() : null;
    }

    public String errorCode() {
        return error != null ? error.code() : null;
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }
}
