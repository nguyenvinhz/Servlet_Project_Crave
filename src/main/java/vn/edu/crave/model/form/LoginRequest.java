package vn.edu.crave.model.form;

public record LoginRequest(String email, String password, Boolean rememberMe) {
}
