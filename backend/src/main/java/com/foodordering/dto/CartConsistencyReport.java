package com.foodordering.dto;

import java.util.List;

/**
 * Báo cáo tổng thể kiểm tra tính nhất quán giữa giỏ hàng và menu.
 */
public record CartConsistencyReport(
        boolean allConsistent,
        int totalItemsChecked,
        int invalidItemsCount,
        List<CartConsistencyItemResult> itemResults
) {
    public CartConsistencyReport {
        if (itemResults == null) {
            itemResults = List.of();
        }
    }
}
