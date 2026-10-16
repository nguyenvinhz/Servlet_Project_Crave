package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.bean.AddToCartForm;
import com.foodordering.bean.CartBean;
import com.foodordering.bean.OrderForm;
import com.foodordering.bean.OrderBean;
import com.foodordering.bean.ProfileBean;
import com.foodordering.bean.RegisterForm;
import com.foodordering.bean.UpdateCartItemForm;
import com.foodordering.bean.ValidatePromotionForm;
import com.foodordering.model.Category;
import com.foodordering.model.CartItem;
import com.foodordering.model.CartItemOption;
import com.foodordering.model.CustomerOrder;
import com.foodordering.model.Payment;
import com.foodordering.model.Food;
import com.foodordering.model.FoodOption;
import com.foodordering.model.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.ErrorCode;
import com.foodordering.enums.FoodStatus;
import com.foodordering.enums.FulfillmentType;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import com.foodordering.enums.OrderStatus;
import com.foodordering.enums.PaymentMethod;
import com.foodordering.enums.PaymentStatus;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.BadRequestException;
import com.foodordering.exception.PromotionValidationException;
import com.foodordering.dao.JpaOrderDAO;
import com.foodordering.dao.JpaAccountDAO;
import com.foodordering.dao.JpaPaymentDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real checkout transaction and MySQL triggers, cleaning only this test's fixtures. */
class CartCheckoutIntegrationTest {
    private static EntityManagerFactory emf;
    private final String token = UUID.randomUUID().toString().replace("-", "");
    private final String categoryId = "XC" + token.substring(0, 8);
    private final String foodId = "XF" + token.substring(0, 8);
    private final String otherFoodId = "XG" + token.substring(0, 8);
    private final String optionId = "XO" + token.substring(0, 8);
    private final String promotionId = "XP" + token.substring(0, 8);
    private final String promotionCode = "XC" + token.substring(0, 24);
    private final String fixtureName = "Checkout integration " + token;
    private final String email = "checkout-it-" + token + "@example.com";
    private final CartService carts = new CartService();
    private final PromotionService promotions = new PromotionService();
    private final CustomerOrderService orders = new CustomerOrderServiceImpl(
            new JpaOrderDAO(), new JpaPaymentDAO(), null,
            new DeliveryFeeService(new DeliveryFeeService.Store("Fixture store", 10.89, 106.76)));
    private ProfileBean owner;

    @BeforeAll
    static void connect() {
        emf = DatabaseConfig.getEntityManagerFactory();
        DatabaseConfig.verifyConnection();
    }

    @AfterAll
    static void close() {
        DatabaseConfig.close();
    }

    @BeforeEach
    void createFixtures() {
        AccountService accounts = new AccountService(new JpaAccountDAO(emf));
        String phone = "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        owner = accounts.register(new RegisterForm(
                fixtureName, email, phone, "Integration#123", "Integration#123"));
        transaction(em -> {
            Category category = new Category(categoryId, fixtureName);
            Food food = new Food(foodId, category, fixtureName, money("40000"), null, null);
            Food otherFood = new Food(otherFoodId, category, fixtureName + " other", money("40000"), null, null);
            FoodOption option = new FoodOption(optionId, food, OptionType.TOPPING, fixtureName, money("10000"));
            Promotion promotion = new Promotion();
            promotion.setPromotionId(promotionId);
            promotion.setCode(promotionCode);
            promotion.setName(fixtureName);
            promotion.setDiscountType(DiscountType.FIXED_AMOUNT);
            promotion.setDiscountValue(money("20000"));
            promotion.setMinimumOrderValue(money("120000"));
            promotion.setStartAt(LocalDateTime.now().minusDays(1));
            promotion.setEndAt(LocalDateTime.now().plusDays(1));
            em.persist(category);
            em.persist(food);
            em.persist(otherFood);
            em.persist(option);
            em.persist(promotion);
        });
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        transaction(em -> {
            if (owner != null) {
                em.createNativeQuery("DELETE FROM customer_order WHERE customer_id = :id AND receiver_name = :name")
                        .setParameter("id", owner.id()).setParameter("name", fixtureName).executeUpdate();
                em.createNativeQuery("DELETE FROM user_account WHERE user_id = :id AND email = :email")
                        .setParameter("id", owner.id()).setParameter("email", email).executeUpdate();
            }
            em.createNativeQuery("DELETE FROM food WHERE food_id = :id AND name = :name")
                    .setParameter("id", foodId).setParameter("name", fixtureName).executeUpdate();
            em.createNativeQuery("DELETE FROM food WHERE food_id = :id AND name = :name")
                    .setParameter("id", otherFoodId).setParameter("name", fixtureName + " other").executeUpdate();
            em.createNativeQuery("DELETE FROM category WHERE category_id = :id AND name = :name")
                    .setParameter("id", categoryId).setParameter("name", fixtureName).executeUpdate();
            em.createNativeQuery("DELETE FROM promotion WHERE promotion_id = :id AND code = :code")
                    .setParameter("id", promotionId).setParameter("code", promotionCode).executeUpdate();
        });
    }

    @ParameterizedTest
    @EnumSource(FulfillmentType.class)
    void checkoutCopiesOptionsPricesVoucherAndPaymentThenClearsCart(FulfillmentType fulfillment) {
        addCart();
        OrderBean order = orders.createOrder(owner.id(), checkout(fulfillment));

        assertEquals(owner.id(), order.getCustomerId());
        assertEquals(OrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertMoney("150000", order.getSubtotal());
        assertMoney("20000", order.getDiscountAmount());
        String expectedFee = fulfillment == FulfillmentType.DELIVERY ? "4000" : "0";
        String expectedTotal = fulfillment == FulfillmentType.DELIVERY ? "134000" : "130000";
        assertMoney(expectedFee, order.getDeliveryFee());
        assertMoney(expectedTotal, order.getTotalAmount());
        assertEquals(1, order.getItems().size());
        var item = order.getItems().get(0);
        assertEquals(foodId, item.getFoodId());
        assertEquals(fixtureName, item.getFoodNameSnapshot());
        assertEquals(3, item.getQuantity());
        assertMoney("50000", item.getUnitPrice());
        assertMoney("150000", item.getLineTotal());
        assertEquals("checkout note", item.getNote());
        assertEquals(1, item.getOptions().size());
        assertEquals(fixtureName, item.getOptions().get(0).getOptionName());
        assertMoney("10000", item.getOptions().get(0).getExtraPrice());
        assertEquals("CASH", order.getPayment().getMethod());
        assertEquals("PENDING", order.getPayment().getStatus());
        assertMoney(expectedTotal, order.getPayment().getAmount());
        assertEquals(1, order.getHistory().size());
        assertEquals("PENDING_CONFIRMATION", order.getHistory().get(0).getStatus());
        assertEquals(0, count("SELECT COUNT(*) FROM cart WHERE customer_id = :customer"));
        assertEquals(1, count("SELECT COUNT(*) FROM customer_order WHERE customer_id = :customer"));
        try (EntityManager em = emf.createEntityManager()) {
            CustomerOrder storedOrder = em.find(CustomerOrder.class, order.getOrderId());
            assertEquals(promotionId, storedOrder.getPromotion().getPromotionId());
            assertEquals(foodId, storedOrder.getOrderDetails().get(0).getFood().getId());
            Object[] stored = (Object[]) em.createNativeQuery("""
                    SELECT o.promotion_id, d.unit_price, d.line_total, opt.option_id, opt.extra_price_snapshot
                    FROM customer_order o JOIN order_detail d ON d.order_id = o.order_id
                    JOIN order_detail_option opt ON opt.order_detail_id = d.order_detail_id
                    WHERE o.order_id = :id AND o.customer_id = :customer
                    """).setParameter("id", order.getOrderId()).setParameter("customer", owner.id()).getSingleResult();
            assertEquals(promotionId, stored[0]);
            assertMoney("50000", (BigDecimal) stored[1]);
            assertMoney("150000", (BigDecimal) stored[2]);
            assertEquals(optionId, stored[3]);
            assertMoney("10000", (BigDecimal) stored[4]);
        }
    }

    @Test
    void persistedBadgeCountSumsQuantitiesWithoutCreatingAnEmptyCart() {
        assertEquals(0, carts.countItems(owner.id()));
        assertEquals(0, count("SELECT COUNT(*) FROM cart WHERE customer_id = :customer"));
        carts.addItem(owner.id(), new AddToCartForm(foodId, 2, null, List.of()));
        carts.addItem(owner.id(), new AddToCartForm(otherFoodId, 3, null, List.of()));
        assertEquals(5, new CartService().countItems(owner.id()));
        carts.clearCart(owner.id());
        assertEquals(0, new CartService().countItems(owner.id()));
    }

    @Test
    void freeDeliveryUsesPersistedFoodSubtotalBeforeVoucherAndMatchesPayment() {
        CartBean cart = addCart();
        carts.updateItem(owner.id(), new com.foodordering.bean.UpdateCartItemForm(cart.getItems().get(0).getCartItemId(), 4, null));
        OrderBean order = orders.createOrder(owner.id(), checkout(FulfillmentType.DELIVERY));
        assertMoney("200000", order.getSubtotal());
        assertMoney("20000", order.getDiscountAmount());
        assertMoney("0", order.getDeliveryFee());
        assertMoney("180000", order.getTotalAmount());
        assertMoney("180000", order.getPayment().getAmount());
        assertTrue(carts.getOrCreateCart(owner.id()).getItems().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"10.94,106.76,8399", "10.96,106.76,14351", "10.8905,106.76,0"})
    void checkoutUsesAndPersistsChosenCoordinatesInsteadOfResolvingTextAgain(double latitude, double longitude, String fee) {
        addCart();
        OrderForm request = checkout(FulfillmentType.DELIVERY);
        request.setDeliveryLatitude(latitude);
        request.setDeliveryLongitude(longitude);
        request.setDeliveryAddress(String.format(java.util.Locale.ROOT, "%.6f, %.6f", latitude, longitude));
        OrderBean order = orders.createOrder(owner.id(), request);
        assertMoney(fee, order.getDeliveryFee());
        assertMoney("150000", order.getSubtotal());
        assertMoney("20000", order.getDiscountAmount());
        assertEquals(0, money("130000").add(money(fee)).compareTo(order.getTotalAmount()));
        assertEquals(latitude, order.getDeliveryLatitude());
        assertEquals(longitude, order.getDeliveryLongitude());
        assertEquals(order.getTotalAmount(), order.getPayment().getAmount());
        try (EntityManager em = emf.createEntityManager()) {
            CustomerOrder saved = em.find(CustomerOrder.class, order.getOrderId());
            assertEquals(latitude, saved.getDeliveryLatitude());
            assertEquals(longitude, saved.getDeliveryLongitude());
            assertEquals(request.getDeliveryAddress(), saved.getDeliveryAddress());
            assertMoney(fee, saved.getDeliveryFee());
        }
    }

    @Test
    void pickupCheckoutClearsStaleDeliveryAddressAndCompletesWithoutDelivery() {
        addCart();
        OrderForm request = checkout(FulfillmentType.PICKUP);
        request.setDeliveryAddress("Địa chỉ còn lại khi đổi sang tự đến lấy");

        OrderBean order = orders.createOrder(owner.id(), request);
        orders.updateOrderStatus(order.getOrderId(), OrderStatus.PREPARING, null, "Đang chuẩn bị");
        orders.updateOrderStatus(order.getOrderId(), OrderStatus.COMPLETED, null, "Khách đã nhận món");
        orders.updateOrderStatus(order.getOrderId(), OrderStatus.COMPLETED, null, "Yêu cầu lặp lại");

        OrderBean completed = orders.getOrderById(order.getOrderId());
        assertNull(completed.getDeliveryAddress());
        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
        assertEquals(3, completed.getHistory().size());
    }

    @Test
    void paymentAndRefundKeepOriginalPaidAtAndRejectInvalidReversal() {
        addCart();
        OrderBean order = orders.createOrder(owner.id(), checkout(FulfillmentType.PICKUP));
        PaymentService payments = new PaymentServiceImpl(new JpaPaymentDAO(), emf);
        String paymentId = order.getPayment().getPaymentId();
        String transactionRef = "IT-" + token;
        payments.updatePaymentStatus(paymentId, PaymentStatus.SUCCESS, transactionRef);
        LocalDateTime originalPaidAt;
        try (EntityManager em = emf.createEntityManager()) {
            originalPaidAt = em.find(Payment.class, paymentId).getPaidAt();
        }

        assertThrows(BadRequestException.class,
                () -> payments.updatePaymentStatus(paymentId, PaymentStatus.FAILED, null));
        payments.updatePaymentStatus(paymentId, PaymentStatus.SUCCESS, "DUPLICATE-" + token);
        payments.updatePaymentStatus(paymentId, PaymentStatus.REFUNDED, null);

        try (EntityManager em = emf.createEntityManager()) {
            Payment stored = em.find(Payment.class, paymentId);
            assertEquals(PaymentStatus.REFUNDED, stored.getStatus());
            assertEquals(originalPaidAt, stored.getPaidAt());
            assertEquals(transactionRef, stored.getTransactionRef());
            assertMoney("130000", stored.getAmount());
        }
    }

    @Test
    void cartRejectsTwoSizesBeforeCreatingAnyItems() {
        String secondOptionId = "XH" + token.substring(0, 8);
        transaction(em -> {
            em.find(FoodOption.class, optionId).setOptionType(OptionType.SIZE);
            em.persist(new FoodOption(secondOptionId, em.find(Food.class, foodId),
                    OptionType.SIZE, fixtureName + " second size", money("10000")));
        });

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> carts.addItem(owner.id(), new AddToCartForm(foodId, 1, null, List.of(optionId, secondOptionId))));

        assertEquals(ErrorCode.INVALID_OPTION, failure.getErrorCode());
        assertEquals(0, count("SELECT COUNT(*) FROM cart WHERE customer_id = :customer"));
        assertNoOrderOrPayment();
    }

    @Test
    void checkoutRevalidatesExclusiveOptionGroupsAndPreservesInvalidCart() {
        CartBean cart = addCart();
        String secondOptionId = "XH" + token.substring(0, 8);
        transaction(em -> {
            em.find(FoodOption.class, optionId).setOptionType(OptionType.SIZE);
            FoodOption second = new FoodOption(secondOptionId, em.find(Food.class, foodId),
                    OptionType.SIZE, fixtureName + " second size", money("10000"));
            em.persist(second);
            em.persist(new CartItemOption(em.find(CartItem.class, cart.getItems().get(0).getCartItemId()), second));
        });

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> orders.createOrder(owner.id(), checkout(FulfillmentType.PICKUP)));

        assertEquals(ErrorCode.INVALID_OPTION, failure.getErrorCode());
        CartBean preserved = carts.getOrCreateCart(owner.id());
        assertEquals(cart.getCartId(), preserved.getCartId());
        assertEquals(2, preserved.getItems().get(0).getOptions().size());
        assertMoney("180000", preserved.getSubtotal());
        assertNoOrderOrPayment();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "EXPIRED", "NOT_STARTED", "MINIMUM", "UNKNOWN"})
    void checkoutRevalidatesVoucherAndRollsBackWithoutLosingCart(String scenario) {
        CartBean cart = addCart();
        assertTrue(promotions.validatePromotion(new ValidatePromotionForm(promotionCode, money("1")), owner.id()).isValid());
        OrderForm request = checkout(FulfillmentType.PICKUP);
        int expectedQuantity = 3;
        String expectedSubtotal = "150000";
        ErrorCode expectedCode;
        switch (scenario) {
            case "INACTIVE" -> {
                transaction(em -> em.find(Promotion.class, promotionId).setStatus(PromotionStatus.INACTIVE));
                expectedCode = ErrorCode.PROMOTION_INACTIVE;
            }
            case "EXPIRED" -> {
                transaction(em -> {
                    Promotion promotion = em.find(Promotion.class, promotionId);
                    promotion.setStartAt(LocalDateTime.now().minusDays(3));
                    promotion.setEndAt(LocalDateTime.now().minusDays(2));
                });
                expectedCode = ErrorCode.PROMOTION_EXPIRED;
            }
            case "NOT_STARTED" -> {
                transaction(em -> {
                    Promotion promotion = em.find(Promotion.class, promotionId);
                    promotion.setStartAt(LocalDateTime.now().plusDays(1));
                    promotion.setEndAt(LocalDateTime.now().plusDays(2));
                });
                expectedCode = ErrorCode.PROMOTION_NOT_STARTED;
            }
            case "MINIMUM" -> {
                carts.updateItem(owner.id(), new UpdateCartItemForm(cart.getItems().get(0).getCartItemId(), 1, null));
                expectedQuantity = 1;
                expectedSubtotal = "50000";
                expectedCode = ErrorCode.PROMOTION_MIN_ORDER_NOT_MET;
            }
            case "UNKNOWN" -> {
                request.setPromotionId("XZ" + token.substring(0, 8));
                expectedCode = ErrorCode.PROMOTION_NOT_FOUND;
            }
            default -> throw new AssertionError(scenario);
        }

        PromotionValidationException failure = assertThrows(PromotionValidationException.class,
                () -> orders.createOrder(owner.id(), request));

        assertEquals(expectedCode, failure.getErrorCode());
        assertEquals(400, failure.getHttpStatus());
        assertCartPreserved(cart.getCartId(), expectedQuantity, expectedSubtotal);
        assertNoOrderOrPayment();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "OTHER_FOOD"})
    void checkoutRejectsChangedOptionsAndPreservesCart(String scenario) {
        CartBean cart = addCart();
        transaction(em -> {
            FoodOption option = em.find(FoodOption.class, optionId);
            if ("DISABLED".equals(scenario)) option.setStatus(OptionStatus.INACTIVE);
            else option.setFood(em.find(Food.class, otherFoodId));
        });

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> orders.createOrder(owner.id(), checkout(FulfillmentType.PICKUP)));

        assertEquals(ErrorCode.INVALID_OPTION, failure.getErrorCode());
        assertEquals(400, failure.getHttpStatus());
        assertCartPreserved(cart.getCartId(), 3, "150000");
        assertNoOrderOrPayment();
    }

    @Test
    void checkoutRejectsUnavailableFoodAndPreservesCart() {
        CartBean cart = addCart();
        transaction(em -> em.find(Food.class, foodId).setStatus(FoodStatus.UNAVAILABLE));

        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> orders.createOrder(owner.id(), checkout(FulfillmentType.PICKUP)));

        assertEquals(ErrorCode.FOOD_NOT_AVAILABLE, failure.getErrorCode());
        assertCartPreserved(cart.getCartId(), 3, "150000");
        assertNoOrderOrPayment();
    }

    @Test
    void emptyAndRepeatedCheckoutReturnBadRequestWithoutExtraOrders() {
        OrderForm request = checkout(FulfillmentType.PICKUP);
        assertEmptyCartFailure(request);
        carts.getOrCreateCart(owner.id());
        assertEmptyCartFailure(request);
        assertNoOrderOrPayment();

        addCart();
        orders.createOrder(owner.id(), request);
        assertEmptyCartFailure(request);
        assertEquals(1, count("SELECT COUNT(*) FROM customer_order WHERE customer_id = :customer"));
        assertEquals(1, count("SELECT COUNT(*) FROM payment p JOIN customer_order o ON o.order_id = p.order_id WHERE o.customer_id = :customer"));
    }

    @Test
    void simultaneousCheckoutConsumesCartOnlyOnce() throws Exception {
        addCart();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Object> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Checkout start timed out");
                try {
                    return orders.createOrder(owner.id(), checkout(FulfillmentType.PICKUP));
                } catch (BadRequestException failure) {
                    return failure;
                }
            };
            Future<Object> first = workers.submit(attempt);
            Future<Object> second = workers.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Object> outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter(OrderBean.class::isInstance).count());
            BadRequestException rejected = outcomes.stream().filter(BadRequestException.class::isInstance)
                    .map(BadRequestException.class::cast).findFirst().orElseThrow();
            assertEquals(ErrorCode.CART_EMPTY, rejected.getErrorCode());
            assertEquals(400, rejected.getHttpStatus());
            assertEquals(1, count("SELECT COUNT(*) FROM customer_order WHERE customer_id = :customer"));
            assertEquals(1, count("SELECT COUNT(*) FROM payment p JOIN customer_order o ON o.order_id = p.order_id WHERE o.customer_id = :customer"));
            assertEquals(0, count("SELECT COUNT(*) FROM cart WHERE customer_id = :customer"));
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void simultaneousCartCreationReturnsTheSamePersistedCart() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<CartBean> attempt = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Cart start timed out");
                return carts.getOrCreateCart(owner.id());
            };
            Future<CartBean> first = workers.submit(attempt);
            Future<CartBean> second = workers.submit(attempt);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(first.get(20, TimeUnit.SECONDS).getCartId(), second.get(20, TimeUnit.SECONDS).getCartId());
            assertEquals(1, count("SELECT COUNT(*) FROM cart WHERE customer_id = :customer"));
        } finally {
            start.countDown();
            workers.shutdownNow();
        }
    }

    private CartBean addCart() {
        return carts.addItem(owner.id(), new AddToCartForm(foodId, 3, "checkout note", List.of(optionId)));
    }

    private OrderForm checkout(FulfillmentType fulfillment) {
        OrderForm request = new OrderForm();
        request.setFulfillmentType(fulfillment);
        request.setReceiverName(fixtureName);
        request.setReceiverPhone(owner.phone());
        request.setDeliveryAddress(fulfillment == FulfillmentType.DELIVERY ? "Checkout fixture address" : null);
        if (fulfillment == FulfillmentType.DELIVERY) {
            // About 2.22 km from the fixed fixture origin: the 1–3 km band.
            request.setDeliveryLatitude(10.91);
            request.setDeliveryLongitude(106.76);
        }
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setPromotionId(promotionId);
        return request;
    }

    private void assertEmptyCartFailure(OrderForm request) {
        BadRequestException failure = assertThrows(BadRequestException.class,
                () -> orders.createOrder(owner.id(), request));
        assertEquals(ErrorCode.CART_EMPTY, failure.getErrorCode());
        assertEquals(400, failure.getHttpStatus());
    }

    private void assertCartPreserved(String expectedCartId, int expectedQuantity, String expectedSubtotal) {
        CartBean preserved = carts.getOrCreateCart(owner.id());
        assertEquals(expectedCartId, preserved.getCartId());
        assertEquals(1, preserved.getItems().size());
        assertEquals(expectedQuantity, preserved.getItems().get(0).getQuantity());
        assertEquals(optionId, preserved.getItems().get(0).getOptions().get(0).getOptionId());
        assertMoney(expectedSubtotal, preserved.getSubtotal());
    }

    private void assertNoOrderOrPayment() {
        assertEquals(0, count("SELECT COUNT(*) FROM customer_order WHERE customer_id = :customer"));
        assertEquals(0, count("SELECT COUNT(*) FROM payment p JOIN customer_order o ON o.order_id = p.order_id WHERE o.customer_id = :customer"));
    }

    private long count(String query) {
        try (EntityManager em = emf.createEntityManager()) {
            return ((Number) em.createNativeQuery(query).setParameter("customer", owner.id()).getSingleResult()).longValue();
        }
    }

    private void transaction(Consumer<EntityManager> work) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                work.accept(em);
                em.getTransaction().commit();
            } catch (RuntimeException | Error exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        }
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }
}
