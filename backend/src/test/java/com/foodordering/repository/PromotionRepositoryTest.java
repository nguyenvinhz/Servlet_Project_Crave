package com.foodordering.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.StoredProcedureQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static jakarta.persistence.ParameterMode.IN;
import static jakarta.persistence.ParameterMode.OUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PromotionRepositoryTest {
    private static final LocalDateTime ORDER_TIME = LocalDateTime.of(2026, 10, 6, 10, 0);

    @Test
    void inMemoryPromotionsDoNotOpenDatabaseConnections() {
        PromotionRepository repository = new PromotionRepository(() -> {
            throw new AssertionError("An in-memory promotion must use Java calculation");
        });

        assertNull(repository.calculateDiscountViaDatabase(null, new BigDecimal("200000"), ORDER_TIME));
        assertNull(repository.calculateDiscountViaDatabase("  ", new BigDecimal("200000"), ORDER_TIME));
    }

    @Test
    void unavailableRoutineFallsBackWithoutRegisteringHiddenOutputParameter() {
        EntityManager em = managerWithVisibleOutputCount(0);

        assertNull(new PromotionRepository(() -> em)
                .calculateDiscountViaDatabase("KM01", new BigDecimal("200000"), ORDER_TIME));

        verify(em, never()).createStoredProcedureQuery(anyString());
        verify(em).close();
    }

    @Test
    void accessibleProcedureReceivesInputsAndReturnsItsDiscount() {
        EntityManager em = managerWithVisibleOutputCount(1);
        StoredProcedureQuery procedure = mock(StoredProcedureQuery.class);
        when(em.createStoredProcedureQuery("sp_calculate_discount")).thenReturn(procedure);
        when(procedure.getOutputParameterValue(4)).thenReturn(new BigDecimal("20000"));

        BigDecimal discount = new PromotionRepository(() -> em)
                .calculateDiscountViaDatabase(" KM01 ", new BigDecimal("200000"), ORDER_TIME);

        assertEquals(new BigDecimal("20000"), discount);
        verify(procedure).registerStoredProcedureParameter(1, String.class, IN);
        verify(procedure).registerStoredProcedureParameter(2, Timestamp.class, IN);
        verify(procedure).registerStoredProcedureParameter(3, BigDecimal.class, IN);
        verify(procedure).registerStoredProcedureParameter(4, BigDecimal.class, OUT);
        verify(procedure).setParameter(1, "KM01");
        verify(procedure).setParameter(2, Timestamp.valueOf(ORDER_TIME));
        verify(procedure).setParameter(3, new BigDecimal("200000"));
        verify(procedure).execute();
        verify(em).close();
    }

    @Test
    void procedureFailureReturnsFallbackAndClosesEntityManager() {
        EntityManager em = managerWithVisibleOutputCount(1);
        StoredProcedureQuery procedure = mock(StoredProcedureQuery.class);
        when(em.createStoredProcedureQuery("sp_calculate_discount")).thenReturn(procedure);
        when(procedure.execute()).thenThrow(new IllegalStateException("Routine unavailable"));

        assertNull(new PromotionRepository(() -> em)
                .calculateDiscountViaDatabase("KM01", new BigDecimal("200000"), ORDER_TIME));

        verify(em).close();
    }

    @Test
    void databaseInitializationFailureAllowsJavaFallback() {
        PromotionRepository repository = new PromotionRepository(() -> {
            throw new IllegalStateException("Offline database");
        });

        assertNull(repository.calculateDiscountViaDatabase("KM01", new BigDecimal("200000"), ORDER_TIME));
    }

    private EntityManager managerWithVisibleOutputCount(long count) {
        EntityManager em = mock(EntityManager.class);
        Query metadata = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(metadata);
        when(metadata.getSingleResult()).thenReturn(count);
        return em;
    }
}
