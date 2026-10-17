package vn.edu.crave.model.viewmodel;

import vn.edu.crave.model.enums.CustomerStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCustomerResponse(String id, String fullName, String email, String phone,
                                    CustomerStatus status, LocalDateTime registeredAt,
                                    long orderCount, BigDecimal totalSpent) {
}
