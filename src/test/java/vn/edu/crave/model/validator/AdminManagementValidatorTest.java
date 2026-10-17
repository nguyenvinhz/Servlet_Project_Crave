package vn.edu.crave.model.validator;

import vn.edu.crave.model.form.AdminPromotionRequest;
import vn.edu.crave.model.form.EmployeeCreateRequest;
import vn.edu.crave.model.enums.DiscountType;
import vn.edu.crave.model.enums.EmployeeRole;
import vn.edu.crave.model.enums.PromotionStatus;
import vn.edu.crave.exception.ValidationException;
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
        AdminPromotionRequest validated = AdminPromotionValidator.validate(new AdminPromotionRequest(" welcome10 ", " Ưu đãi ",
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
        EmployeeCreateRequest validated = AdminAccountValidator.validateEmployee(new EmployeeCreateRequest(" Nhân viên ",
                " STAFF@EXAMPLE.COM ", " 0987654321 ", " Created#123 ", EmployeeRole.ORDER_STAFF, null, LocalDate.of(2026, 10, 1)));
        assertEquals("staff@example.com", validated.email());
        assertEquals("0987654321", validated.phone());
        assertEquals(" Created#123 ", validated.password());
    }

    @Test
    void missingOrMalformedEmployeeDataIsRejected() {
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(null));
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(new EmployeeCreateRequest("", "bad", "123", "tiny", null, null, null)));
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(new EmployeeCreateRequest("Nhân viên", "test@example.com", "0987654321", "bad\0password", EmployeeRole.ORDER_STAFF, null, LocalDate.now())));
        assertThrows(ValidationException.class, () -> AdminAccountValidator.validateEmployee(new EmployeeCreateRequest("Nhân viên", "test@example.com", "0987654321", "Created#123", EmployeeRole.ORDER_STAFF, null, LocalDate.of(999, 1, 1))));
    }

    private AdminPromotionRequest request(BigDecimal discount, LocalDateTime startAt, LocalDateTime endAt) {
        return new AdminPromotionRequest("VALID", "Ưu đãi", DiscountType.PERCENT, discount, BigDecimal.ZERO, null,
                startAt, endAt, PromotionStatus.ACTIVE);
    }
}
