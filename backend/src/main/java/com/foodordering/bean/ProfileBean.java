package com.foodordering.bean;

import com.foodordering.enums.AccountType;
import com.foodordering.enums.EmployeeRole;

public record ProfileBean(String id, AccountType accountType, String fullName,
                              String email, String phone,
                              EmployeeRole role,
                              long credentialVersion)
        implements java.io.Serializable {

    public ProfileBean(String id, AccountType accountType, String fullName,
                           String email, String phone, EmployeeRole role) {
        this(id, accountType, fullName, email, phone, role, 0L);
    }

    // JSP EL resolves these JavaBean accessors.
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

    public EmployeeRole getRole() {
        return role;
    }
}
