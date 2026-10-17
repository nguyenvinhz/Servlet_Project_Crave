package vn.edu.crave.model.viewmodel;

import vn.edu.crave.model.enums.AccountType;
import vn.edu.crave.model.enums.EmployeeRole;

public record ProfileResponse(String id, AccountType accountType, String fullName,
                              String email, String phone,
                              EmployeeRole role, long credentialVersion)
        implements java.io.Serializable {

    public ProfileResponse(String id, AccountType accountType, String fullName,
                           String email, String phone, EmployeeRole role) {
        this(id, accountType, fullName, email, phone, role, 0L);
    }

    // JSP EL resolves JavaBean accessors.
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
