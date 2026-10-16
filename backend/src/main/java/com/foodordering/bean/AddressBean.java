package com.foodordering.bean;

import java.time.LocalDateTime;

public record AddressBean(String id, String addressLine, String note, boolean isDefault,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
