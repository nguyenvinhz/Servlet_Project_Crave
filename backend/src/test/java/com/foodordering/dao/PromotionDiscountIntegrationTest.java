package com.foodordering.dao;

import com.foodordering.config.DatabaseConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the installed MySQL discount procedure through its real order triggers. */
class PromotionDiscountIntegrationTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 31, 23, 59, 59);
    private static final LocalDateTime ORDER_TIME = LocalDateTime.of(2026, 10, 15, 12, 0);
    private static final BigDecimal DELIVERY_FEE = money("2000");
    private static EntityManagerFactory emf;

    private final String token = UUID.randomUUID().toString().replace("-", "");
    private final String customerId = "DC" + token.substring(0, 8);
    private final String promotionId = "DP" + token.substring(0, 8);
    private final String otherPromotionId = "DQ" + token.substring(0, 8);
    private final String orderId = "DO" + token.substring(0, 8);
    private EntityManager em;

    @BeforeAll
    static void connect() {
        emf = DatabaseConfig.getEntityManagerFactory();
        DatabaseConfig.verifyConnection();
    }

    @AfterAll
    static void closeDatabase() {
        DatabaseConfig.close();
    }

    @BeforeEach
    void createCustomerInsideRollbackOnlyTransaction() {
        em = emf.createEntityManager();
        em.getTransaction().begin();
        em.createNativeQuery("""
                INSERT INTO user_account
                    (user_id, account_type, full_name, email, phone, password_hash)
                VALUES (:id, 'CUSTOMER', 'Discount regression', :email, :phone, 'test-only')
                """)
                .setParameter("id", customerId)
                .setParameter("email", "discount-it-" + token + "@example.test")
                .setParameter("phone", "09" + ThreadLocalRandom.current()
                        .nextLong(1_000_000_000_000L, 10_000_000_000_000L))
                .executeUpdate();
        em.createNativeQuery("INSERT INTO customer (customer_id) VALUES (:id)")
                .setParameter("id", customerId).executeUpdate();
    }

    @AfterEach
    void rollbackAllOwnedFixtures() {
        if (em != null) {
            try {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
            } finally {
                em.close();
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("insertCases")
    void insertedOrdersRespectPromotionEligibilityRoundingAndCaps(InsertCase scenario) {
        createPromotion(promotionId, scenario.status(), scenario.type(), scenario.value(),
                scenario.minimum(), scenario.maximum());
        createOrder(scenario.hasPromotion() ? promotionId : null, scenario.subtotal(), scenario.time());

        assertOrderAmounts(scenario.expectedDiscount(), scenario.subtotal());
    }

    private static Stream<InsertCase> insertCases() {
        return Stream.of(
                insertCase("active fixed amount", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "50000", ORDER_TIME, "20000"),
                insertCase("active percent rounds half up", "ACTIVE", "PERCENT", "10", "0", null,
                        "50005", ORDER_TIME, "5001"),
                insertCase("active percent maximum", "ACTIVE", "PERCENT", "10", "0", "7500",
                        "100000", ORDER_TIME, "7500"),
                insertCase("active fixed maximum", "ACTIVE", "FIXED_AMOUNT", "20000", "0", "12000",
                        "50000", ORDER_TIME, "12000"),
                insertCase("fixed amount cannot exceed subtotal", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "5000", ORDER_TIME, "5000"),
                insertCase("inactive fixed amount", "INACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "50000", ORDER_TIME, "0"),
                insertCase("inactive percent", "INACTIVE", "PERCENT", "10", "0", null,
                        "50000", ORDER_TIME, "0"),
                insertCase("subtotal below minimum", "ACTIVE", "FIXED_AMOUNT", "20000", "50001", null,
                        "50000", ORDER_TIME, "0"),
                insertCase("minimum boundary qualifies", "ACTIVE", "FIXED_AMOUNT", "20000", "50000", null,
                        "50000", ORDER_TIME, "20000"),
                insertCase("before validity period", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "50000", START.minusSeconds(1), "0"),
                insertCase("after validity period", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "50000", END.plusSeconds(1), "0"),
                insertCase("start boundary qualifies", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "50000", START, "20000"),
                insertCase("end boundary qualifies", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "50000", END, "20000"),
                insertCase("zero subtotal", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "0", ORDER_TIME, "0"),
                new InsertCase("no promotion", "ACTIVE", "FIXED_AMOUNT", "20000", "0", null,
                        "50000", ORDER_TIME, "0", false));
    }

    @Test
    void changingSubtotalAfterPromotionDeactivationClearsDiscount() {
        createPromotion(promotionId, "ACTIVE", "FIXED_AMOUNT", "20000", "0", null);
        createOrder(promotionId, "50000", ORDER_TIME);
        assertOrderAmounts("20000", "50000");

        deactivatePromotion();
        updateSubtotal("60000");

        assertOrderAmounts("0", "60000");
    }

    @Test
    void changingPromotionToAnInactiveVoucherClearsDiscount() {
        createPromotion(promotionId, "ACTIVE", "FIXED_AMOUNT", "20000", "0", null);
        createPromotion(otherPromotionId, "INACTIVE", "PERCENT", "10", "0", null);
        createOrder(promotionId, "50000", ORDER_TIME);
        assertOrderAmounts("20000", "50000");

        updateOrderPromotion(otherPromotionId);

        assertOrderAmounts("0", "50000");
    }

    @Test
    void changingOrderTimeRechecksTheValidityPeriod() {
        createPromotion(promotionId, "ACTIVE", "FIXED_AMOUNT", "20000", "0", null);
        createOrder(promotionId, "50000", ORDER_TIME);
        assertOrderAmounts("20000", "50000");

        updateOrderTime(END.plusSeconds(1));
        assertOrderAmounts("0", "50000");

        updateOrderTime(ORDER_TIME);
        assertOrderAmounts("20000", "50000");
    }

    @Test
    void changingToAnActivePromotionAndRemovingItRecalculatesDiscount() {
        createPromotion(promotionId, "ACTIVE", "FIXED_AMOUNT", "20000", "0", null);
        createPromotion(otherPromotionId, "ACTIVE", "PERCENT", "10", "0", null);
        createOrder(promotionId, "50000", ORDER_TIME);
        assertOrderAmounts("20000", "50000");

        updateOrderPromotion(otherPromotionId);
        assertOrderAmounts("5000", "50000");

        updateOrderPromotion(null);
        assertOrderAmounts("0", "50000");
    }

    @Test
    void changingSubtotalRecalculatesPercentDiscountAndRespectsItsMaximum() {
        createPromotion(promotionId, "ACTIVE", "PERCENT", "10", "0", "8000");
        createOrder(promotionId, "50005", ORDER_TIME);
        assertOrderAmounts("5001", "50005");

        updateSubtotal("75000");
        assertOrderAmounts("7500", "75000");

        updateSubtotal("100000");
        assertOrderAmounts("8000", "100000");
    }

    @Test
    void statusOnlyUpdatesPreserveHistoricDiscountAfterPromotionDeactivation() {
        createPromotion(promotionId, "ACTIVE", "FIXED_AMOUNT", "20000", "0", null);
        createOrder(promotionId, "50000", ORDER_TIME);
        assertOrderAmounts("20000", "50000");

        deactivatePromotion();
        em.createNativeQuery("UPDATE customer_order SET status = 'PREPARING' WHERE order_id = :id")
                .setParameter("id", orderId).executeUpdate();

        assertOrderAmounts("20000", "50000");
        assertEquals("PREPARING", em.createNativeQuery("SELECT status FROM customer_order WHERE order_id = :id")
                .setParameter("id", orderId).getSingleResult());
    }

    private void createPromotion(String id, String status, String type,
                                 String value, String minimum, String maximum) {
        em.createNativeQuery("""
                INSERT INTO promotion
                    (promotion_id, code, name, discount_type, discount_value,
                     minimum_order_value, maximum_discount, start_at, end_at, status)
                VALUES (:id, :code, 'Discount regression', :type, :value,
                        :minimum, :maximum, :start, :end, :status)
                """)
                .setParameter("id", id).setParameter("code", id + token.substring(8, 24))
                .setParameter("type", type).setParameter("value", money(value))
                .setParameter("minimum", money(minimum))
                .setParameter("maximum", maximum == null ? null : money(maximum))
                .setParameter("start", Timestamp.valueOf(START)).setParameter("end", Timestamp.valueOf(END))
                .setParameter("status", status).executeUpdate();
    }

    private void createOrder(String voucherId, String subtotal, LocalDateTime time) {
        em.createNativeQuery("""
                INSERT INTO customer_order
                    (order_id, customer_id, promotion_id, ordered_at, fulfillment_type,
                     receiver_name, receiver_phone, delivery_address, subtotal, discount_amount, delivery_fee)
                VALUES (:id, :customer, :promotion, :time, 'DELIVERY',
                        'Discount regression', '0900000000', 'Regression fixture', :subtotal, 0, :fee)
                """)
                .setParameter("id", orderId).setParameter("customer", customerId)
                .setParameter("promotion", voucherId).setParameter("time", Timestamp.valueOf(time))
                .setParameter("subtotal", money(subtotal)).setParameter("fee", DELIVERY_FEE).executeUpdate();
    }

    private void deactivatePromotion() {
        em.createNativeQuery("UPDATE promotion SET status = 'INACTIVE' WHERE promotion_id = :id")
                .setParameter("id", promotionId).executeUpdate();
    }

    private void updateSubtotal(String subtotal) {
        em.createNativeQuery("UPDATE customer_order SET subtotal = :subtotal WHERE order_id = :id")
                .setParameter("subtotal", money(subtotal)).setParameter("id", orderId).executeUpdate();
    }

    private void updateOrderPromotion(String voucherId) {
        em.createNativeQuery("UPDATE customer_order SET promotion_id = :promotion WHERE order_id = :id")
                .setParameter("promotion", voucherId).setParameter("id", orderId).executeUpdate();
    }

    private void updateOrderTime(LocalDateTime time) {
        em.createNativeQuery("UPDATE customer_order SET ordered_at = :time WHERE order_id = :id")
                .setParameter("time", Timestamp.valueOf(time)).setParameter("id", orderId).executeUpdate();
    }

    private void assertOrderAmounts(String expectedDiscount, String expectedSubtotal) {
        Object[] amounts = (Object[]) em.createNativeQuery("""
                SELECT subtotal, discount_amount, total_amount
                FROM customer_order WHERE order_id = :id
                """).setParameter("id", orderId).getSingleResult();
        assertEquals(money(expectedSubtotal).stripTrailingZeros(),
                ((BigDecimal) amounts[0]).stripTrailingZeros(), "subtotal");
        assertEquals(money(expectedDiscount).stripTrailingZeros(),
                ((BigDecimal) amounts[1]).stripTrailingZeros(), "discount_amount");
        BigDecimal expectedTotal = money(expectedSubtotal).subtract(money(expectedDiscount)).add(DELIVERY_FEE);
        assertEquals(expectedTotal.stripTrailingZeros(),
                ((BigDecimal) amounts[2]).stripTrailingZeros(), "generated total_amount");
    }

    private static InsertCase insertCase(String name, String status, String type, String value,
                                         String minimum, String maximum, String subtotal,
                                         LocalDateTime time, String expectedDiscount) {
        return new InsertCase(name, status, type, value, minimum, maximum, subtotal, time, expectedDiscount, true);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private record InsertCase(String name, String status, String type, String value,
                              String minimum, String maximum, String subtotal,
                              LocalDateTime time, String expectedDiscount, boolean hasPromotion) {
        @Override
        public String toString() {
            return name;
        }
    }
}
