package vn.edu.crave.model.service;

import vn.edu.crave.config.DatabaseConfig;
import vn.edu.crave.model.enums.OrderStatus;
import vn.edu.crave.exception.ValidationException;
import vn.edu.crave.model.dao.CustomerOrderDAO;
import vn.edu.crave.model.dao.PaymentDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregates persisted orders; no generated or demonstration metrics are used. */
public class AdminOverviewService {
    private final EntityManagerFactory emf;
    private final CustomerOrderService orders;

    public AdminOverviewService() {
        this(DatabaseConfig.getEntityManagerFactory(),
                new CustomerOrderService(new CustomerOrderDAO(), new PaymentDAO()));
    }

    public AdminOverviewService(EntityManagerFactory emf, CustomerOrderService orders) {
        this.emf = emf;
        this.orders = orders;
    }

    public Map<String, Object> overview(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365
                || from.getYear() < 1000 || to.getYear() > 9998) {
            throw new ValidationException("Chọn khoảng ngày hợp lệ, tối đa 366 ngày.");
        }
        try (EntityManager em = emf.createEntityManager()) {
            Object[] counts = (Object[]) query(em, """
                    SELECT COUNT(*),
                           COALESCE(SUM(CASE WHEN o.status = 'COMPLETED' AND p.status = 'SUCCESS' THEN o.total_amount ELSE 0 END), 0),
                           COALESCE(SUM(o.status = 'COMPLETED'), 0),
                           COALESCE(SUM(o.status = 'PENDING_CONFIRMATION'), 0),
                           COALESCE(SUM(o.status = 'CANCELLED'), 0),
                           COALESCE(SUM(o.status = 'COMPLETED' AND p.status = 'SUCCESS'), 0)
                    FROM customer_order o LEFT JOIN payment p ON p.order_id = o.order_id
                    WHERE o.ordered_at >= :start AND o.ordered_at < :end
                    """, from, to).getSingleResult();
            BigDecimal revenue = decimal(counts[1]);
            long paidOrders = number(counts[5]);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("revenue", revenue);
            summary.put("totalOrders", number(counts[0]));
            summary.put("completedOrders", number(counts[2]));
            summary.put("pendingOrders", number(counts[3]));
            summary.put("cancelledOrders", number(counts[4]));
            summary.put("averageOrderValue", paidOrders == 0 ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(paidOrders), 0, RoundingMode.HALF_UP));
            summary.put("totalCustomers", ((Number) em.createNativeQuery("SELECT COUNT(*) FROM customer").getSingleResult()).longValue());
            summary.put("activeFoods", ((Number) em.createNativeQuery("SELECT COUNT(*) FROM food WHERE status = 'AVAILABLE'").getSingleResult()).longValue());

            @SuppressWarnings("unchecked")
            List<Object[]> dailyRows = query(em, """
                    SELECT DATE(o.ordered_at), COUNT(*),
                           COALESCE(SUM(CASE WHEN o.status = 'COMPLETED' AND p.status = 'SUCCESS' THEN o.total_amount ELSE 0 END), 0)
                    FROM customer_order o LEFT JOIN payment p ON p.order_id = o.order_id
                    WHERE o.ordered_at >= :start AND o.ordered_at < :end
                    GROUP BY DATE(o.ordered_at) ORDER BY DATE(o.ordered_at)
                    """, from, to).getResultList();
            Map<String, Object[]> indexedDays = new LinkedHashMap<>();
            dailyRows.forEach(row -> indexedDays.put(row[0].toString(), row));
            List<Map<String, Object>> daily = new ArrayList<>();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                Object[] row = indexedDays.get(date.toString());
                daily.add(Map.of("date", date.toString(), "orderCount", row == null ? 0L : number(row[1]),
                        "revenue", row == null ? BigDecimal.ZERO : decimal(row[2])));
            }

            Map<String, Long> statusCounts = new LinkedHashMap<>();
            for (OrderStatus status : OrderStatus.values()) statusCounts.put(status.name(), 0L);
            @SuppressWarnings("unchecked")
            List<Object[]> statusRows = query(em, """
                    SELECT o.status, COUNT(*) FROM customer_order o
                    WHERE o.ordered_at >= :start AND o.ordered_at < :end GROUP BY o.status
                    """, from, to).getResultList();
            statusRows.forEach(row -> statusCounts.put(row[0].toString(), number(row[1])));

            @SuppressWarnings("unchecked")
            List<Object[]> itemRows = query(em, """
                    SELECT od.food_id, od.food_name_snapshot, SUM(od.quantity), SUM(od.line_total)
                    FROM order_detail od JOIN customer_order o ON o.order_id = od.order_id
                    JOIN payment p ON p.order_id = o.order_id
                    WHERE o.ordered_at >= :start AND o.ordered_at < :end
                      AND o.status = 'COMPLETED' AND p.status = 'SUCCESS'
                    GROUP BY od.food_id, od.food_name_snapshot ORDER BY SUM(od.quantity) DESC, od.food_id LIMIT 8
                    """, from, to).getResultList();
            List<Map<String, Object>> topItems = itemRows.stream().map(row -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("foodId", row[0]);
                item.put("name", row[1]);
                item.put("quantity", number(row[2]));
                item.put("revenue", decimal(row[3]));
                return item;
            }).toList();

            @SuppressWarnings("unchecked")
            List<String> recentIds = query(em, """
                    SELECT o.order_id FROM customer_order o
                    WHERE o.ordered_at >= :start AND o.ordered_at < :end
                    ORDER BY o.ordered_at DESC, o.order_id DESC LIMIT 6
                    """, from, to).getResultList();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("range", Map.of("from", from.toString(), "to", to.toString()));
            result.put("summary", summary);
            result.put("daily", daily);
            result.put("statusCounts", statusCounts);
            result.put("topItems", topItems);
            result.put("recentOrders", recentIds.stream().map(orders::getOrderById).toList());
            return result;
        }
    }

    private static jakarta.persistence.Query query(EntityManager em, String sql, LocalDate from, LocalDate to) {
        return em.createNativeQuery(sql).setParameter("start", from.atStartOfDay()).setParameter("end", to.plusDays(1).atStartOfDay());
    }

    private static long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }
}
