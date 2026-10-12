package com.foodordering.dto;

import com.foodordering.enums.EmployeeRole;
import java.time.LocalDate;

public record EmployeeCreateRequest(String fullName, String email, String phone, String password,
                                    EmployeeRole role, String address, LocalDate hireDate) {
}
