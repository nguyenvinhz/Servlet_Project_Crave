package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.dto.AddToCartRequest;
import com.foodordering.dto.CartDto;
import com.foodordering.dto.ProfileResponse;
import com.foodordering.dto.RegisterRequest;
import com.foodordering.dto.UpdateCartItemRequest;
import com.foodordering.dto.ValidatePromotionRequest;
import com.foodordering.entity.Category;
import com.foodordering.entity.Food;
import com.foodordering.entity.FoodOption;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.OptionType;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.repository.JpaAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real cart/voucher repositories with isolated, automatically cleaned MySQL fixtures. */
class CartPromotionIntegrationTest {
    private static EntityManagerFactory emf;
    private final Map<String, String> accountEmails = new LinkedHashMap<>();
    private final String token = UUID.randomUUID().toString().replace("-", "");
    private final String categoryId = "IC" + token.substring(0, 8);
    private final String foodId = "IF" + token.substring(0, 8);
    private final String optionId = "IO" + token.substring(0, 8);
    private final String promotionId = "IP" + token.substring(0, 8);
    private final String fixtureName = "Cart integration " + token;
    private final String promotionCode = "IT" + token.substring(0, 24);
    private final CartService carts = new CartService();
    private final PromotionService promotions = new PromotionService();
    private ProfileResponse owner;
    private ProfileResponse other;

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
        AccountService accounts = new AccountService(new JpaAccountRepository(emf));
        owner = register(accounts);
        other = register(accounts);
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                Category category = new Category(categoryId, fixtureName);
                Food food = new Food(foodId, category, fixtureName, money("40000"), null, null);
                FoodOption option = new FoodOption(optionId, food, OptionType.TOPPING,
                        fixtureName, money("10000"));
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
                em.persist(option);
                em.persist(promotion);
                em.getTransaction().commit();
            } catch (RuntimeException exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        }
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                for (Map.Entry<String, String> account : accountEmails.entrySet()) {
                    em.createNativeQuery("DELETE FROM user_account WHERE user_id = :id AND email = :email")
                            .setParameter("id", account.getKey()).setParameter("email", account.getValue())
                            .executeUpdate();
                }
                em.createNativeQuery("DELETE FROM food WHERE food_id = :id AND name = :name")
                        .setParameter("id", foodId).setParameter("name", fixtureName).executeUpdate();
                em.createNativeQuery("DELETE FROM category WHERE category_id = :id AND name = :name")
                        .setParameter("id", categoryId).setParameter("name", fixtureName).executeUpdate();
                em.createNativeQuery("DELETE FROM promotion WHERE promotion_id = :id AND code = :code")
                        .setParameter("id", promotionId).setParameter("code", promotionCode).executeUpdate();
                em.getTransaction().commit();
            } catch (RuntimeException exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw exception;
            }
        }
    }

    @Test
    void optionTotalsVoucherAndMutationsPreserveCustomerOwnership() {
        CartDto cart = carts.addItem(owner.id(), add(1));
        assertMoney("50000", cart.getSubtotal());
        assertMoney("40000", cart.getItems().get(0).getBasePrice());
        assertMoney("10000", cart.getItems().get(0).getOptionTotal());
        assertFalse(validate("999999").isValid(), "Client subtotal must not qualify a smaller cart");

        cart = carts.addItem(owner.id(), add(2));
        assertEquals(1, cart.getItems().size(), "Identical options should merge into one item");
        assertEquals(3, cart.getItems().get(0).getQuantity());
        assertMoney("150000", cart.getSubtotal());
        var voucher = validate("1");
        assertTrue(voucher.isValid());
        assertMoney("150000", voucher.getOriginalSubtotal());
        assertMoney("20000", voucher.getDiscountAmount());
        assertMoney("130000", voucher.getFinalSubtotal());

        String itemId = cart.getItems().get(0).getCartItemId();
        assertTrue(carts.getOrCreateCart(other.id()).getItems().isEmpty());
        assertThrows(ResourceNotFoundException.class,
                () -> carts.updateItem(other.id(), new UpdateCartItemRequest(itemId, 1, "foreign write")));
        assertThrows(ResourceNotFoundException.class, () -> carts.removeItem(other.id(), itemId));
        carts.clearCart(other.id());
        assertMoney("150000", carts.getVerifiedSubtotal(owner.id()));

        cart = carts.updateItem(owner.id(), new UpdateCartItemRequest(itemId, 4, "owned update"));
        assertMoney("200000", cart.getSubtotal());
        assertEquals("owned update", cart.getItems().get(0).getNote());
        assertTrue(carts.removeItem(owner.id(), itemId).getItems().isEmpty());
        assertMoney("0", carts.getVerifiedSubtotal(owner.id()));
        assertFalse(validate("999999").isValid());
        carts.addItem(owner.id(), add(3));
        assertTrue(carts.clearCart(owner.id()).getItems().isEmpty());
    }

    @Test
    void voucherRevalidationUsesPersistedCartAfterQuantityChanges() {
        CartDto cart = carts.addItem(owner.id(), add(3));
        assertTrue(validate("0").isValid());
        String itemId = cart.getItems().get(0).getCartItemId();
        carts.updateItem(owner.id(), new UpdateCartItemRequest(itemId, 1, null));
        assertMoney("50000", carts.getVerifiedSubtotal(owner.id()));
        assertFalse(validate("150000").isValid(), "Stale client subtotal must not preserve the voucher");
        assertTrue(carts.updateItem(owner.id(), new UpdateCartItemRequest(itemId, 0, null)).getItems().isEmpty());
        assertFalse(validate("150000").isValid());
    }

    private ProfileResponse register(AccountService accounts) {
        String email = "cart-it-" + UUID.randomUUID().toString().replace("-", "") + "@example.com";
        String phone = "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        ProfileResponse profile = accounts.register(new RegisterRequest(
                "Cart Integration", email, phone, "Integration#123", "Integration#123"));
        accountEmails.put(profile.id(), email);
        return profile;
    }

    private AddToCartRequest add(int quantity) {
        return new AddToCartRequest(foodId, quantity, "integration note", List.of(optionId));
    }

    private com.foodordering.dto.PromotionValidationResultDto validate(String clientSubtotal) {
        return promotions.validatePromotion(new ValidatePromotionRequest(promotionCode, money(clientSubtotal)), owner.id());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }
}
