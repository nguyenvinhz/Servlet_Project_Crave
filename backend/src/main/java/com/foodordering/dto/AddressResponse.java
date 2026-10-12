package com.foodordering.dto;

import java.time.LocalDateTime;

public record AddressResponse(String id, String addressLine, String note, boolean isDefault,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
