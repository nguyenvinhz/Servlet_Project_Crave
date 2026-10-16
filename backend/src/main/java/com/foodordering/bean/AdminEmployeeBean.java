package com.foodordering.bean;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import java.time.LocalDate;

public record AdminEmployeeBean(String id, String fullName, String email, String phone,
                                    EmployeeRole role, EmployeeStatus status, String address,
                                    LocalDate hireDate) {
    public String getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public EmployeeRole getRole() { return role; }
    public EmployeeStatus getStatus() { return status; }
    public String getAddress() { return address; }
    public LocalDate getHireDate() { return hireDate; }
}
