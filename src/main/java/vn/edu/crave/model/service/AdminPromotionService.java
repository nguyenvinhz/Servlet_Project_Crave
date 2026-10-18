package vn.edu.crave.model.service;

import vn.edu.crave.config.DatabaseConfig;
import vn.edu.crave.model.form.AdminPromotionRequest;
import vn.edu.crave.model.viewmodel.PromotionDto;
import vn.edu.crave.model.entity.Promotion;
import vn.edu.crave.model.enums.DiscountType;
import vn.edu.crave.model.enums.PromotionStatus;
import vn.edu.crave.exception.ConflictException;
import vn.edu.crave.exception.ResourceNotFoundException;
import vn.edu.crave.exception.ValidationException;
import vn.edu.crave.model.validator.AdminPromotionValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;

import java.util.ArrayList;
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

    public List<PromotionDto> listPromotions() {
        try (EntityManager em = emf.createEntityManager()) {
            List<Promotion> promotions = em.createQuery("SELECT p FROM Promotion p ORDER BY p.createdAt DESC, p.promotionId", Promotion.class)
                    .getResultList();
            List<PromotionDto> results = new ArrayList<>();
            for (Promotion promotion : promotions) {
                if (promotion != null) {
                    results.add(createPromotionDto(promotion));
                }
            }
            return results;
        }
    }

    public PromotionDto createPromotion(AdminPromotionRequest request) {
        AdminPromotionRequest validated = AdminPromotionValidator.validate(request);
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
            return createPromotionDto(promotion);
        });
    }

    public PromotionDto updatePromotion(String id, AdminPromotionRequest request) {
        AdminPromotionRequest validated = AdminPromotionValidator.validate(request);
        return write(em -> {
            Promotion promotion = requirePromotion(em, id);
            requireUniqueCode(em, validated.code(), id);
            apply(promotion, validated);
            em.flush();
            return createPromotionDto(promotion);
        });
    }

    public PromotionDto updatePromotionStatus(String id, PromotionStatus status) {
        if (status == null) throw new ValidationException("Vui lòng chọn trạng thái khuyến mãi.");
        return write(em -> {
            Promotion promotion = requirePromotion(em, id);
            promotion.setStatus(status);
            em.flush();
            return createPromotionDto(promotion);
        });
    }

    private PromotionDto createPromotionDto(Promotion promotion) {
        PromotionDto dto = new PromotionDto();
        dto.setPromotionId(promotion.getPromotionId());
        dto.setCode(promotion.getCode());
        dto.setName(promotion.getName());
        dto.setDiscountType(promotion.getDiscountType());
        dto.setDiscountValue(promotion.getDiscountValue());
        dto.setMinimumOrderValue(promotion.getMinimumOrderValue());
        dto.setMaximumDiscount(promotion.getMaximumDiscount());
        dto.setStartAt(promotion.getStartAt());
        dto.setEndAt(promotion.getEndAt());
        dto.setStatus(promotion.getStatus());

        if (promotion.getDiscountType() == DiscountType.PERCENT) {
            String maximumDiscount = (promotion.getMaximumDiscount() != null && promotion.getMaximumDiscount().doubleValue() > 0)
                    ? String.format(", tối đa %,.0f đ", promotion.getMaximumDiscount().doubleValue())
                    : "";
            dto.setDescription(String.format("Giảm %s%% cho đơn từ %,.0f đ%s",
                    promotion.getDiscountValue() != null ? promotion.getDiscountValue().toPlainString() : "0",
                    promotion.getMinimumOrderValue() != null ? promotion.getMinimumOrderValue().doubleValue() : 0,
                    maximumDiscount));
        } else {
            dto.setDescription(String.format("Giảm %,.0f đ cho đơn từ %,.0f đ",
                    promotion.getDiscountValue() != null ? promotion.getDiscountValue().doubleValue() : 0,
                    promotion.getMinimumOrderValue() != null ? promotion.getMinimumOrderValue().doubleValue() : 0));
        }
        return dto;
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

    private static void apply(Promotion promotion, AdminPromotionRequest request) {
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
