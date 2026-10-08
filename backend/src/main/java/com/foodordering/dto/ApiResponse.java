package com.foodordering.dto;

import com.foodordering.enums.ErrorCode;

public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(boolean success, String message, T data) {
        this(success, message, data, (Object) null);
    }

    public ApiResponse(boolean success, String message, T data, Object errorCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        if (errorCode instanceof ErrorCode) {
            this.errorCode = ((ErrorCode) errorCode).getCode();
        } else if (errorCode != null) {
            this.errorCode = errorCode.toString();
        } else {
            this.errorCode = null;
        }
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> error(String message, Object errorCode) {
        return new ApiResponse<>(false, message, null, errorCode);
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
