package com.foodordering.bean;

public record RegisterForm(String fullName, String email, String phone,
                              String password, String confirmPassword) {
}
