package com.foodordering.dto;

public record AddressWriteRequest(String addressLine, String note, Boolean isDefault) {
}
