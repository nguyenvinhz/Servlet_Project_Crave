package vn.edu.crave.model.viewmodel;

import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.EmployeeStatus;
import java.time.LocalDate;

public record AdminEmployeeResponse(String id, String fullName, String email, String phone,
                                    EmployeeRole role, EmployeeStatus status, String address,
                                    LocalDate hireDate) {
}
