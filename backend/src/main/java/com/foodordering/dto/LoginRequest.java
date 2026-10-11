package com.foodordering.dto;

public record LoginRequest(String email, String password, Boolean rememberMe) {
}
