package vn.edu.crave.model.form;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yêu cầu kiểm tra tính nhất quán giữa món trong giỏ hàng và dữ liệu menu thời gian thực.
 */
public record CartConsistencyItemRequest(
        String foodId,
        BigDecimal expectedUnitPrice,
        List<String> optionIds
) {
    public CartConsistencyItemRequest {
        if (optionIds == null) {
            optionIds = List.of();
        }
    }
}
