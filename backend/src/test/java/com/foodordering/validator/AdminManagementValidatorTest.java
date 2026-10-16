package com.foodordering.validator;

import com.foodordering.bean.AdminPromotionForm;
import com.foodordering.bean.EmployeeCreateForm;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.EmployeeRole;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AdminManagementValidatorTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "10.5", "1000000000000"})
    void invalidPercentageAmountsCannotReachDatabase(String discount) {
        assertThrows(ValidationException.class, () -> AdminPromotionValidator.validate(request(new BigDecimal(discount), start, start.plusDays(1))));
    }

    @Test
    void promotionCodeAndDefaultsAreNormalized() {
        AdminPromotionForm validated = AdminPromotionValidator.validate(new AdminPromotionForm(" welcome10 ", " Ưu đãi ",
                DiscountType.PERCENT, BigDecimal.TEN, null, null, start, start.plusDays(1), null));
        assertEquals("WELCOME10", validated.code());
        assertEquals("Ưu đãi", validated.name());
        assertEquals(BigDecimal.ZERO, validated.minimumOrderValue());
        assertEquals(PromotionStatus.ACTIVE, validated.status());
    }

    @Test
    void promotionEndMustFollowStart() {
        assertThrows(ValidationException.class, () -> AdminPromotionValidator.validate(request(BigDecimal.TEN, start, start)));
        assertThrows(ValidationException.class, () -> AdminPromotionValidator.validate(request(BigDecimal.TEN, start, start.minusDays(1))));
        assertThrows(ValidationException.class, () -> AdminPromotionValidator.validate(request(BigDecimal.TEN, start, start.plusNanos(1))));
        assertThrows(ValidationException.class, () -> AdminPromotionValidator.validate(request(BigDecimal.TEN, start.withYear(999), start)));
    }

    @Test
    void employeeContactNormalizationPreservesPasswordText() {
        EmployeeCreateForm validated = AdminAccountValidator.validateEmployee(new EmployeeCreateForm(" Nhân viên ",
                " STAFF@EXAMPLE.COM ", " 0987654321 ", " Created#123 ", EmployeeRole.ORDER_STAFF, null, LocalDate.of(2026, 10, 1)));
        assertEquals("staff@example.com", validated.email());
        assertEquals("0987654321", validated.phone());
        assertEquals(" Created#123 ", validated.password());
    }

    @Test
    void missingOrMalformedEmployeeDataIsRejected() {
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(null));
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(new EmployeeCreateForm("", "bad", "123", "tiny", null, null, null)));
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(new EmployeeCreateForm("Nhân viên", "test@example.com", "0987654321", "bad\0password", EmployeeRole.ORDER_STAFF, null, LocalDate.now())));
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(new EmployeeCreateForm("Nhân viên", "test@example.com", "0987654321", "Created#123", EmployeeRole.ORDER_STAFF, null, LocalDate.of(999, 1, 1))));
    }

    private AdminPromotionForm request(BigDecimal discount, LocalDateTime startAt, LocalDateTime endAt) {
        return new AdminPromotionForm("VALID", "Ưu đãi", DiscountType.PERCENT, discount, BigDecimal.ZERO, null,
                startAt, endAt, PromotionStatus.ACTIVE);
    }
}
