package com.foodordering.dto;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import java.time.LocalDate;

public record AdminEmployeeResponse(String id, String fullName, String email, String phone,
                                    EmployeeRole role, EmployeeStatus status, String address,
                                    LocalDate hireDate) {
}
