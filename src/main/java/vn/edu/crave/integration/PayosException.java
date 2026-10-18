package vn.edu.crave.integration;

import vn.edu.crave.exception.AppException;

/** A sanitized gateway error; provider response bodies and credentials are never retained. */
public class PayosException extends AppException {
    private final String code;

    public PayosException(String code, int statusCode) {
        super(statusCode == 400 ? "PAYOS_INVALID_WEBHOOK" : "PAYOS_GATEWAY_ERROR",
                statusCode == 400 ? "Dữ liệu xác nhận thanh toán không hợp lệ."
                        : statusCode == 503 ? "Dịch vụ thanh toán tạm thời không khả dụng."
                        : "Không thể xác thực phản hồi từ dịch vụ thanh toán.",
                statusCode);
        this.code = code != null && code.matches("(?:[0-9]{2,5}|[A-Z_]{1,40})")
                ? code : "PROVIDER_ERROR";
    }

    public String getCode() {
        return code;
    }
}
