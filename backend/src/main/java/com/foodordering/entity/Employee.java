package com.foodordering.entity;

import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.EmployeeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "employee")
@PrimaryKeyJoinColumn(name = "employee_id")
@DiscriminatorValue("EMPLOYEE")
public class Employee extends User {

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30, nullable = false)
    private EmployeeRole role;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private EmployeeStatus status = EmployeeStatus.WORKING;

    protected Employee() {
    }

    public Employee(String id, String fullName, String email, String phone, String passwordHash,
                    EmployeeRole role, String address, LocalDate hireDate) {
        super(id, fullName, email, phone, passwordHash);
        this.role = role;
        this.address = address;
        this.hireDate = hireDate;
        this.status = EmployeeStatus.WORKING;
    }

    public Employee(String id, String fullName, String email, String phone, String passwordHash,
                    EmployeeRole role, String address, LocalDate hireDate, EmployeeStatus status) {
        super(id, fullName, email, phone, passwordHash);
        this.role = role;
        this.address = address;
        this.hireDate = hireDate;
        this.status = status;
    }

    public EmployeeRole getRole() {
        return role;
    }

    public void setRole(EmployeeRole role) {
        this.role = role;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public void setStatus(EmployeeStatus status) {
        this.status = status;
    }
}
