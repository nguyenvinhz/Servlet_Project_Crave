package com.foodordering.dto;

import com.foodordering.enums.OptionStatus;

/**
 * Request DTO cập nhật trạng thái tùy chọn món ăn (ACTIVE / INACTIVE).
 */
public record FoodOptionStatusUpdateRequest(
        OptionStatus status
) {
}
