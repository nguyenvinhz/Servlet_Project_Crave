package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.exception.ValidationException;
import com.foodordering.dao.JpaOrderDAO;
import com.foodordering.dao.JpaPaymentDAO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminOverviewIntegrationTest {
    @AfterAll
    static void close() {
        DatabaseConfig.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void dailyAndStatusBreakdownsReconcileWithDatabaseTotals() {
        var service = new AdminOverviewService(DatabaseConfig.getEntityManagerFactory(),
                new CustomerOrderServiceImpl(new JpaOrderDAO(), new JpaPaymentDAO()));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(90);
        Map<String, Object> result = service.overview(from, to);
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        List<Map<String, Object>> daily = (List<Map<String, Object>>) result.get("daily");
        Map<String, Long> statusCounts = (Map<String, Long>) result.get("statusCounts");
        assertEquals(91, daily.size());
        assertEquals(summary.get("totalOrders"), daily.stream().mapToLong(row -> (Long) row.get("orderCount")).sum());
        assertEquals(summary.get("totalOrders"), statusCounts.values().stream().mapToLong(Long::longValue).sum());
        BigDecimal dailyRevenue = daily.stream().map(row -> (BigDecimal) row.get("revenue")).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, ((BigDecimal) summary.get("revenue")).compareTo(dailyRevenue));
        try (var em = DatabaseConfig.getEntityManagerFactory().createEntityManager()) {
            BigDecimal expected = (BigDecimal) em.createNativeQuery("""
                    SELECT COALESCE(SUM(o.total_amount), 0) FROM customer_order o
                    JOIN payment p ON p.order_id = o.order_id
                    WHERE o.status = 'COMPLETED' AND p.status = 'SUCCESS'
                      AND o.ordered_at >= :start AND o.ordered_at < :end
                    """).setParameter("start", from.atStartOfDay()).setParameter("end", to.plusDays(1).atStartOfDay()).getSingleResult();
            assertEquals(0, expected.compareTo((BigDecimal) summary.get("revenue")));
        }
    }

    @Test
    void invalidRangeIsRejectedBeforeReadingDatabase() {
        var service = new AdminOverviewService(null, null);
        assertThrows(ValidationException.class, () -> service.overview(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 12)));
        assertThrows(ValidationException.class, () -> service.overview(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)));
    }
}
