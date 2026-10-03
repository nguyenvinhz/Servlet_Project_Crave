package com.foodordering.enums;

/**
 * Bảng mã lỗi chuẩn dùng chung cho toàn bộ hệ thống API (Quy ước do Ung Văn Trí phụ trách).
 */
public enum ErrorCode {
    SUCCESS("SUCCESS", "Thành công"),
    BAD_REQUEST("BAD_REQUEST", "Yêu cầu không hợp lệ"),
    UNAUTHORIZED("UNAUTHORIZED", "Chưa xác thực danh tính"),
    FORBIDDEN("FORBIDDEN", "Không có quyền thực hiện thao tác này"),
    NOT_FOUND("NOT_FOUND", "Không tìm thấy tài nguyên yêu cầu"),

    // Domain Cart
    CART_NOT_FOUND("CART_NOT_FOUND", "Không tìm thấy giỏ hàng của khách hàng"),
    CART_EMPTY("CART_EMPTY", "Giỏ hàng hiện đang trống"),
    CART_ITEM_NOT_FOUND("CART_ITEM_NOT_FOUND", "Món trong giỏ hàng không tồn tại hoặc đã bị xóa"),
    INVALID_QUANTITY("INVALID_QUANTITY", "Số lượng món không hợp lệ (phải lớn hơn 0)"),
    FOOD_NOT_AVAILABLE("FOOD_NOT_AVAILABLE", "Món ăn tạm ngưng phục vụ hoặc không tồn tại"),
    INVALID_OPTION("INVALID_OPTION", "Tùy chọn món ăn không hợp lệ hoặc đã ngừng áp dụng"),

    // Domain Promotion
    PROMOTION_NOT_FOUND("PROMOTION_NOT_FOUND", "Mã khuyến mãi không tồn tại"),
    PROMOTION_INACTIVE("PROMOTION_INACTIVE", "Mã khuyến mãi hiện đang tạm khóa hoặc chưa kích hoạt"),
    PROMOTION_EXPIRED("PROMOTION_EXPIRED", "Mã khuyến mãi đã hết hạn sử dụng"),
    PROMOTION_NOT_STARTED("PROMOTION_NOT_STARTED", "Chương trình khuyến mãi chưa đến thời gian áp dụng"),
    PROMOTION_MIN_ORDER_NOT_MET("PROMOTION_MIN_ORDER_NOT_MET", "Giá trị đơn hàng chưa đạt mức tối thiểu để áp dụng mã"),

    // System
    DATABASE_ERROR("DATABASE_ERROR", "Lỗi thao tác cơ sở dữ liệu"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Lỗi hệ thống máy chủ nội bộ");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
