package com.foodordering.dao;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.FoodOption;
import com.foodordering.enums.OptionStatus;
import com.foodordering.enums.OptionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.util.List;
import java.util.Optional;

public class FoodOptionDAO {

    private final EntityManagerFactory emf;

    public FoodOptionDAO() {
        this(DatabaseConfig.getEntityManagerFactory());
    }

    public FoodOptionDAO(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public List<FoodOption> findByFoodId(String foodId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    "SELECT o FROM FoodOption o WHERE o.food.id = :foodId ORDER BY o.optionType ASC, o.id ASC", FoodOption.class)
                    .setParameter("foodId", foodId)
                    .getResultList();
        }
    }

    public List<FoodOption> findActiveByFoodId(String foodId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    "SELECT o FROM FoodOption o WHERE o.food.id = :foodId AND o.status = :status ORDER BY o.optionType ASC, o.id ASC", FoodOption.class)
                    .setParameter("foodId", foodId)
                    .setParameter("status", OptionStatus.ACTIVE)
                    .getResultList();
        }
    }

    public Optional<FoodOption> findById(String id) {
        try (EntityManager em = emf.createEntityManager()) {
            FoodOption option = em.find(FoodOption.class, id);
            return Optional.ofNullable(option);
        }
    }

    public boolean existsById(String id) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery("SELECT COUNT(o) FROM FoodOption o WHERE o.id = :id", Long.class)
                    .setParameter("id", id)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    public boolean existsByFoodIdAndTypeAndName(String foodId, OptionType type, String name) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery(
                    "SELECT COUNT(o) FROM FoodOption o WHERE o.food.id = :foodId AND o.optionType = :type AND LOWER(o.name) = LOWER(:name)", Long.class)
                    .setParameter("foodId", foodId)
                    .setParameter("type", type)
                    .setParameter("name", name)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    public boolean existsByFoodIdAndTypeAndNameAndIdNot(String foodId, OptionType type, String name, String optionId) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery(
                    "SELECT COUNT(o) FROM FoodOption o WHERE o.food.id = :foodId AND o.optionType = :type AND LOWER(o.name) = LOWER(:name) AND o.id <> :optionId", Long.class)
                    .setParameter("foodId", foodId)
                    .setParameter("type", type)
                    .setParameter("name", name)
                    .setParameter("optionId", optionId)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    public FoodOption save(FoodOption option) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(option);
            tx.commit();
            return option;
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public FoodOption update(FoodOption option) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            FoodOption merged = em.merge(option);
            tx.commit();
            return merged;
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void deleteById(String id) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            FoodOption option = em.find(FoodOption.class, id);
            if (option != null) {
                em.remove(option);
            }
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public String generateNextId() {
        return "TC" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
