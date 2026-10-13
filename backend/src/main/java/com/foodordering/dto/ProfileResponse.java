package com.foodordering.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;

public record ProfileResponse(String id, AccountType accountType, String fullName,
                              String email, String phone,
                              @JsonInclude(JsonInclude.Include.NON_NULL) EmployeeRole role,
                              @JsonIgnore long credentialVersion)
        implements java.io.Serializable {

    public ProfileResponse(String id, AccountType accountType, String fullName,
                           String email, String phone, EmployeeRole role) {
        this(id, accountType, fullName, email, phone, role, 0L);
    }

    // JSP EL resolves bean accessors; record components remain the JSON/API contract.
    public String getId() {
        return id;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public EmployeeRole getRole() {
        return role;
    }
}
