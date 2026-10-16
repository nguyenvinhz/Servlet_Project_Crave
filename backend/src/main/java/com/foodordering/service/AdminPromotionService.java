package com.foodordering.service;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.bean.AdminPromotionForm;
import com.foodordering.bean.PromotionBean;
import com.foodordering.model.Promotion;
import com.foodordering.enums.PromotionStatus;
import com.foodordering.exception.ConflictException;
import com.foodordering.exception.ResourceNotFoundException;
import com.foodordering.exception.ValidationException;
import com.foodordering.validator.AdminPromotionValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

public class AdminPromotionService {
    private final EntityManagerFactory emf;

    public AdminPromotionService() {
        this(DatabaseConfig.getEntityManagerFactory());
    }

    public AdminPromotionService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public List<PromotionBean> listPromotions() {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("SELECT p FROM Promotion p ORDER BY p.createdAt DESC, p.promotionId", Promotion.class)
                    .getResultList().stream().map(PromotionBean::new).toList();
        }
    }

    public PromotionBean createPromotion(AdminPromotionForm request) {
        AdminPromotionForm validated = AdminPromotionValidator.validate(request);
        return write(em -> {
            requireUniqueCode(em, validated.code(), null);
            Promotion promotion = new Promotion();
            String id;
            do {
                id = "KM" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            } while (em.find(Promotion.class, id) != null);
            promotion.setPromotionId(id);
            apply(promotion, validated);
            em.persist(promotion);
            em.flush();
            return new PromotionBean(promotion);
        });
    }

    public PromotionBean updatePromotion(String id, AdminPromotionForm request) {
        AdminPromotionForm validated = AdminPromotionValidator.validate(request);
        return write(em -> {
            Promotion promotion = requirePromotion(em, id);
            requireUniqueCode(em, validated.code(), id);
            apply(promotion, validated);
            em.flush();
            return new PromotionBean(promotion);
        });
    }

    public PromotionBean updatePromotionStatus(String id, PromotionStatus status) {
        if (status == null) throw new ValidationException("Vui lòng chọn trạng thái khuyến mãi.");
        return write(em -> {
            Promotion promotion = requirePromotion(em, id);
            promotion.setStatus(status);
            em.flush();
            return new PromotionBean(promotion);
        });
    }

    private static Promotion requirePromotion(EntityManager em, String id) {
        Promotion promotion = em.find(Promotion.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (promotion == null) throw new ResourceNotFoundException("Không tìm thấy khuyến mãi.");
        return promotion;
    }

    private static void requireUniqueCode(EntityManager em, String code, String excludedId) {
        String jpql = "SELECT COUNT(p) FROM Promotion p WHERE UPPER(p.code) = :code"
                + (excludedId == null ? "" : " AND p.promotionId <> :id");
        var query = em.createQuery(jpql, Long.class).setParameter("code", code);
        if (excludedId != null) query.setParameter("id", excludedId);
        if (query.getSingleResult() > 0) throw new ConflictException("Mã khuyến mãi đã tồn tại.");
    }

    private static void apply(Promotion promotion, AdminPromotionForm request) {
        promotion.setCode(request.code());
        promotion.setName(request.name());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        promotion.setMinimumOrderValue(request.minimumOrderValue());
        promotion.setMaximumDiscount(request.maximumDiscount());
        promotion.setStartAt(request.startAt());
        promotion.setEndAt(request.endAt());
        promotion.setStatus(request.status());
    }

    private <T> T write(Function<EntityManager, T> work) {
        try (EntityManager em = emf.createEntityManager()) {
            em.getTransaction().begin();
            try {
                T result = work.apply(em);
                em.getTransaction().commit();
                return result;
            } catch (RuntimeException exception) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                for (Throwable current = exception; current != null; current = current.getCause()) {
                    if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                            && "23000".equals(violation.getSQLState())) {
                        throw new ConflictException("Mã khuyến mãi đã tồn tại.");
                    }
                }
                throw exception;
            }
        }
    }
}
