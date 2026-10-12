package com.foodordering.validator;

import com.foodordering.dto.EmployeeCreateRequest;
import com.foodordering.exception.ValidationException;
import com.foodordering.utils.PasswordHasher;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AdminAccountValidator {
    private AdminAccountValidator() {
    }

    public static EmployeeCreateRequest validateEmployee(EmployeeCreateRequest request) {
        if (request == null) throw new ValidationException("Vui lòng cung cấp thông tin nhân viên.");
        Map<String, String> errors = new LinkedHashMap<>();
        String name = trim(request.fullName());
        String email = trim(request.email()).toLowerCase(Locale.ROOT);
        String phone = trim(request.phone());
        String address = request.address() == null ? null : request.address().strip();
        if (name.isBlank() || name.length() > 100 || invalidText(name)) {
            errors.put("fullName", "Họ tên không được để trống hoặc vượt quá 100 ký tự.");
        }
        if (email.length() > 100 || invalidText(email) || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            errors.put("email", "Email không đúng định dạng hoặc vượt quá 100 ký tự.");
        }
        if (phone.length() < 9 || phone.length() > 15 || !phone.matches("\\+?[0-9]{8,15}")) {
            errors.put("phone", "Số điện thoại phải có 9–15 ký tự, gồm chữ số và dấu + ở đầu.");
        }
        if (request.password() == null || request.password().isBlank() || request.password().length() < 8
                || request.password().length() > 72 || !PasswordHasher.isValidPasswordText(request.password())) {
            errors.put("password", "Mật khẩu phải có 8–72 ký tự hợp lệ.");
        }
        if (request.role() == null) errors.put("role", "Vui lòng chọn chức vụ.");
        if (request.hireDate() == null) errors.put("hireDate", "Vui lòng chọn ngày vào làm.");
        else if (request.hireDate().getYear() < 1000 || request.hireDate().getYear() > 9999) {
            errors.put("hireDate", "Ngày vào làm phải có năm từ 1000 đến 9999.");
        }
        if (address != null && (address.length() > 255 || invalidText(address))) {
            errors.put("address", "Địa chỉ không được vượt quá 255 ký tự.");
        }
        if (!errors.isEmpty()) throw new ValidationException("Thông tin nhân viên không hợp lệ.", errors);
        return new EmployeeCreateRequest(name, email, phone, request.password(), request.role(), address, request.hireDate());
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean invalidText(String value) {
        return !PasswordHasher.isValidPasswordText(value) || value.chars().anyMatch(Character::isISOControl);
    }
}
