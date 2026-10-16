package com.foodordering.dao;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.model.*;
import com.foodordering.enums.*;
import com.foodordering.exception.BadRequestException;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartItemCustomizationDAOTest {
    @Test
    void retainsUnchangedRowsAndCommitsAllCustomizationTogether() {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        Cart cart = new Cart("GH01", (Customer) null);
        Food food = new Food("MA01", "Food", new BigDecimal("40000"));
        FoodOption kept = new FoodOption("TC01", food, OptionType.TOPPING, "Kept", BigDecimal.ONE);
        FoodOption removed = new FoodOption("TC02", food, OptionType.TOPPING, "Removed", BigDecimal.ONE);
        FoodOption added = new FoodOption("TC03", food, OptionType.TOPPING, "Added", BigDecimal.ONE);
        CartItem item = new CartItem("CI01", cart, food, 1, "old");
        CartItemOption keptRow = new CartItemOption(item, kept);
        item.setItemOptions(new ArrayList<>(List.of(keptRow, new CartItemOption(item, removed))));
        when(em.getTransaction()).thenReturn(tx);
        when(em.find(Cart.class, "GH01", LockModeType.PESSIMISTIC_WRITE)).thenReturn(cart);
        when(em.find(CartItem.class, "CI01", LockModeType.PESSIMISTIC_WRITE)).thenReturn(item);
        @SuppressWarnings("unchecked") TypedQuery<FoodOption> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(FoodOption.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(kept, added));
        try (var config = mockStatic(DatabaseConfig.class)) {
            config.when(DatabaseConfig::getEntityManager).thenReturn(em);
            new CartItemDAO().updateItemWithOptions("CI01", "GH01", 3, "new", List.of("TC01", "TC03"));
            assertEquals(3, item.getQuantity());
            assertEquals("new", item.getNote());
            assertSame(keptRow, item.getItemOptions().get(0));
            assertEquals(List.of("TC01", "TC03"), item.getOptionIds());
            verify(em, times(1)).persist(any(CartItemOption.class));
            var ordered = inOrder(em, tx);
            ordered.verify(tx).begin();
            ordered.verify(em).find(Cart.class, "GH01", LockModeType.PESSIMISTIC_WRITE);
            ordered.verify(em).find(CartItem.class, "CI01", LockModeType.PESSIMISTIC_WRITE);
            ordered.verify(tx).commit();
            verify(em).close();
        }
    }

    @Test
    void revalidatesOptionsInsideTransactionAndRollsBackBeforeMutatingItem() {
        EntityManager em = mock(EntityManager.class);
        EntityTransaction tx = mock(EntityTransaction.class);
        Cart cart = new Cart("GH01", (Customer) null);
        CartItem item = new CartItem("CI01", cart, new Food("MA01", "Food", BigDecimal.ONE), 2, "old");
        when(em.getTransaction()).thenReturn(tx);
        when(tx.isActive()).thenReturn(true);
        when(em.find(Cart.class, "GH01", LockModeType.PESSIMISTIC_WRITE)).thenReturn(cart);
        when(em.find(CartItem.class, "CI01", LockModeType.PESSIMISTIC_WRITE)).thenReturn(item);
        @SuppressWarnings("unchecked") TypedQuery<FoodOption> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(FoodOption.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        try (var config = mockStatic(DatabaseConfig.class)) {
            config.when(DatabaseConfig::getEntityManager).thenReturn(em);
            assertThrows(BadRequestException.class,
                    () -> new CartItemDAO().updateItemWithOptions("CI01", "GH01", 3, "new", List.of("INACTIVE")));
            assertEquals(2, item.getQuantity());
            assertEquals("old", item.getNote());
            verify(tx).rollback();
            verify(tx, never()).commit();
            verify(em).close();
        }
    }
}
