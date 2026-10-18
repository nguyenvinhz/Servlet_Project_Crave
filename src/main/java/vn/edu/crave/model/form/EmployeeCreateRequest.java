package vn.edu.crave.model.form;

import vn.edu.crave.model.enums.EmployeeRole;
import java.time.LocalDate;

public record EmployeeCreateRequest(String fullName, String email, String phone, String password,
                                    EmployeeRole role, String address, LocalDate hireDate) {
}
