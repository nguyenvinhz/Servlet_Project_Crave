package com.foodordering.dto;

import com.foodordering.enums.ErrorCode;

/**
 * Định dạng phản hồi JSON chuẩn dùng chung cho toàn bộ API (Convention do Ung Văn Trí phụ trách).
 *
 * @param <T> Kiểu dữ liệu chứa trong trường data
 */
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(boolean success, String message, T data, String errorCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Thành công", data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, ErrorCode.BAD_REQUEST.getCode());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getDefaultMessage(), null, errorCode.getCode());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String customMessage) {
        return new ApiResponse<>(false, customMessage, null, errorCode.getCode());
    }

    public static <T> ApiResponse<T> error(String errorCode, String customMessage) {
        return new ApiResponse<>(false, customMessage, null, errorCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
