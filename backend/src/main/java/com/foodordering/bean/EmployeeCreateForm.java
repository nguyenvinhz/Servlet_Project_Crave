package com.foodordering.bean;

import com.foodordering.enums.EmployeeRole;
import java.time.LocalDate;

public record EmployeeCreateForm(String fullName, String email, String phone, String password,
                                    EmployeeRole role, String address, LocalDate hireDate) {
}
