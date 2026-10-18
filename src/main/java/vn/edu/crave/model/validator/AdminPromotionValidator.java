package vn.edu.crave.model.validator;

import vn.edu.crave.model.form.AdminPromotionRequest;
import vn.edu.crave.model.enums.DiscountType;
import vn.edu.crave.model.enums.PromotionStatus;
import vn.edu.crave.exception.ValidationException;
import vn.edu.crave.util.PasswordHasher;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class AdminPromotionValidator {
    private AdminPromotionValidator() {
    }

    public static AdminPromotionRequest validate(AdminPromotionRequest request) {
        if (request == null) throw new ValidationException("Vui lòng cung cấp thông tin khuyến mãi.");
        Map<String, String> errors = new LinkedHashMap<>();
        String code = request.code() == null ? "" : request.code().strip().toUpperCase(Locale.ROOT);
        String name = request.name() == null ? "" : request.name().strip();
        if (!code.matches("[A-Z0-9_-]{1,30}")) {
            errors.put("code", "Mã khuyến mãi phải có 1–30 chữ cái, chữ số, dấu gạch dưới hoặc gạch ngang.");
        }
        if (name.isBlank() || name.length() > 150 || !PasswordHasher.isValidPasswordText(name)
                || name.chars().anyMatch(Character::isISOControl)) {
            errors.put("name", "Tên khuyến mãi không được để trống hoặc vượt quá 150 ký tự.");
        }
        if (request.discountType() == null) errors.put("discountType", "Vui lòng chọn loại giảm giá.");
        money(request.discountValue(), true, "discountValue", errors);
        if (request.discountType() == DiscountType.PERCENT && request.discountValue() != null
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.put("discountValue", "Phần trăm giảm giá phải lớn hơn 0 và không vượt quá 100.");
        }
        BigDecimal minimum = request.minimumOrderValue() == null ? BigDecimal.ZERO : request.minimumOrderValue();
        money(minimum, false, "minimumOrderValue", errors);
        if (request.maximumDiscount() != null) money(request.maximumDiscount(), true, "maximumDiscount", errors);
        if (request.startAt() == null) errors.put("startAt", "Vui lòng chọn thời gian bắt đầu.");
        else if (request.startAt().getYear() < 1000 || request.startAt().getYear() > 9999) {
            errors.put("startAt", "Thời gian bắt đầu phải có năm từ 1000 đến 9999.");
        }
        if (request.endAt() == null) errors.put("endAt", "Vui lòng chọn thời gian kết thúc.");
        else if (request.endAt().getYear() < 1000 || request.endAt().getYear() > 9999) {
            errors.put("endAt", "Thời gian kết thúc phải có năm từ 1000 đến 9999.");
        }
        if (request.startAt() != null && request.endAt() != null
                && !request.endAt().withNano(0).isAfter(request.startAt().withNano(0))) {
            errors.put("endAt", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        if (!errors.isEmpty()) throw new ValidationException("Thông tin khuyến mãi không hợp lệ.", errors);
        return new AdminPromotionRequest(code, name, request.discountType(), request.discountValue(), minimum,
                request.maximumDiscount(), request.startAt().withNano(0), request.endAt().withNano(0),
                request.status() == null ? PromotionStatus.ACTIVE : request.status());
    }

    private static void money(BigDecimal value, boolean positive, String field, Map<String, String> errors) {
        if (value == null || (positive ? value.signum() <= 0 : value.signum() < 0)
                || value.stripTrailingZeros().scale() > 0 || value.compareTo(new BigDecimal("999999999999")) > 0) {
            errors.put(field, "Giá trị phải là số nguyên " + (positive ? "lớn hơn 0" : "không âm") + ", tối đa 12 chữ số.");
        }
    }
}
