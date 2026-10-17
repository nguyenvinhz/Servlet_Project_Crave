package com.foodordering.dto;

import java.time.LocalDateTime;

public record AddressResponse(String id, String addressLine, String note, boolean isDefault,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    public String getId() { return id; }
    public String getAddressLine() { return addressLine; }
    public String getNote() { return note; }
    public boolean getIsDefault() { return isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
