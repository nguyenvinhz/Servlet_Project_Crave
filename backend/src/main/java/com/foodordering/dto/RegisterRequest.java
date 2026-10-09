package com.foodordering.dto;

public record RegisterRequest(String fullName, String email, String phone,
                              String password, String confirmPassword) {
}
