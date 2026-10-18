package vn.edu.crave.model.form;

public record RegisterRequest(String fullName, String email, String phone,
                              String password, String confirmPassword) {
}
