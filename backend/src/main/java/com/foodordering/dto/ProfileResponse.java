package com.foodordering.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;

public record ProfileResponse(String id, AccountType accountType, String fullName,
                              String email, String phone,
                              @JsonInclude(JsonInclude.Include.NON_NULL) EmployeeRole role)
        implements java.io.Serializable {

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
