package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.PromotionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class PromotionRepository {

    public Promotion findByCode(String code) {
        if (code == null) return null;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return null;
        try {
            List<Promotion> list = em.createQuery("SELECT p FROM Promotion p WHERE p.code = :code", Promotion.class)
                    .setParameter("code", code.trim())
                    .getResultList();
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi JPA khi tìm khuyến mãi theo code: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Tìm khuyến mãi theo promotion_id.
     */
    public Promotion findById(String promotionId) {
        if (promotionId == null) return null;
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return null;
        try {
            return em.find(Promotion.class, promotionId.trim());
        } catch (Exception e) {
            throw new RuntimeException("Lỗi JPA khi tìm khuyến mãi theo ID: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Lấy danh sách tất cả các mã khuyến mãi đang trong thời gian hiệu lực và có status = 'ACTIVE'.
     */
    public List<Promotion> findAllActive(LocalDateTime currentTime) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return Collections.emptyList();
        try {
            LocalDateTime checkTime = currentTime != null ? currentTime : LocalDateTime.now();
            return em.createQuery("SELECT p FROM Promotion p WHERE p.status = :status AND :checkTime BETWEEN p.startAt AND p.endAt ORDER BY p.minimumOrderValue ASC", Promotion.class)
                    .setParameter("status", PromotionStatus.ACTIVE)
                    .setParameter("checkTime", checkTime)
                    .getResultList();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi JPA khi lấy danh sách khuyến mãi đang hoạt động: " + e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    /**
     * Gọi Stored Procedure sp_calculate_discount trong MySQL qua JPA StoredProcedureQuery.
     */
    public BigDecimal calculateDiscountViaDatabase(String promotionId, BigDecimal subtotal, LocalDateTime orderTime) {
        EntityManager em = DatabaseConfig.getEntityManager();
        if (em == null) return null;
        try {
            StoredProcedureQuery query = em.createStoredProcedureQuery("sp_calculate_discount");
            query.registerStoredProcedureParameter(1, String.class, ParameterMode.IN);
            query.registerStoredProcedureParameter(2, Timestamp.class, ParameterMode.IN);
            query.registerStoredProcedureParameter(3, BigDecimal.class, ParameterMode.IN);
            query.registerStoredProcedureParameter(4, BigDecimal.class, ParameterMode.OUT);

            query.setParameter(1, promotionId);
            query.setParameter(2, Timestamp.valueOf(orderTime != null ? orderTime : LocalDateTime.now()));
            query.setParameter(3, subtotal != null ? subtotal : BigDecimal.ZERO);

            query.execute();
            Object out = query.getOutputParameterValue(4);
            return out != null ? new BigDecimal(out.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            // Nếu stored procedure chưa sẵn sàng hoặc môi trường offline, fallback tính toán Java
            return null;
        } finally {
            em.close();
        }
    }
}
