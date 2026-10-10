package com.foodordering.dto;

import com.foodordering.enums.FoodStatus;

public record FoodStatusUpdateRequest(FoodStatus status) {
}
