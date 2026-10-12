package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Food;
import com.foodordering.enums.FoodStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JpaFoodRepository implements FoodRepository {

    private final EntityManagerFactory emf;

    public JpaFoodRepository() {
        this(DatabaseConfig.getEntityManagerFactory());
    }

    public JpaFoodRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public List<Food> findAll() {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    "SELECT f FROM Food f JOIN FETCH f.category ORDER BY f.id ASC", Food.class)
                    .getResultList();
        }
    }

    @Override
    public List<Food> findAvailable() {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    "SELECT f FROM Food f JOIN FETCH f.category WHERE f.status = :status ORDER BY f.id ASC", Food.class)
                    .setParameter("status", FoodStatus.AVAILABLE)
                    .getResultList();
        }
    }

    @Override
    public List<Food> findByCategoryId(String categoryId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    "SELECT f FROM Food f JOIN FETCH f.category WHERE f.category.id = :categoryId ORDER BY f.id ASC", Food.class)
                    .setParameter("categoryId", categoryId)
                    .getResultList();
        }
    }

    @Override
    public List<Food> findAvailableByCategoryId(String categoryId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                    "SELECT f FROM Food f JOIN FETCH f.category WHERE f.category.id = :categoryId AND f.status = :status ORDER BY f.id ASC", Food.class)
                    .setParameter("categoryId", categoryId)
                    .setParameter("status", FoodStatus.AVAILABLE)
                    .getResultList();
        }
    }

    @Override
    public List<Food> search(String keyword, String categoryId, FoodStatus status) {
        try (EntityManager em = emf.createEntityManager()) {
            StringBuilder jpql = new StringBuilder("SELECT f FROM Food f JOIN FETCH f.category WHERE 1=1");
            List<String> conditions = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                jpql.append(" AND LOWER(f.name) LIKE LOWER(:keyword)");
            }
            if (categoryId != null && !categoryId.trim().isEmpty()) {
                jpql.append(" AND f.category.id = :categoryId");
            }
            if (status != null) {
                jpql.append(" AND f.status = :status");
            }
            jpql.append(" ORDER BY f.id ASC");

            TypedQuery<Food> query = em.createQuery(jpql.toString(), Food.class);
            if (keyword != null && !keyword.trim().isEmpty()) {
                query.setParameter("keyword", "%" + keyword.trim() + "%");
            }
            if (categoryId != null && !categoryId.trim().isEmpty()) {
                query.setParameter("categoryId", categoryId.trim());
            }
            if (status != null) {
                query.setParameter("status", status);
            }

            return query.getResultList();
        }
    }

    @Override
    public Optional<Food> findById(String id) {
        try (EntityManager em = emf.createEntityManager()) {
            try {
                Food food = em.createQuery(
                        "SELECT f FROM Food f JOIN FETCH f.category WHERE f.id = :id", Food.class)
                        .setParameter("id", id)
                        .getSingleResult();
                return Optional.of(food);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public Optional<Food> findByIdWithOptions(String id) {
        try (EntityManager em = emf.createEntityManager()) {
            try {
                Food food = em.createQuery(
                        "SELECT DISTINCT f FROM Food f JOIN FETCH f.category LEFT JOIN FETCH f.options WHERE f.id = :id", Food.class)
                        .setParameter("id", id)
                        .getSingleResult();
                return Optional.of(food);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean existsById(String id) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery("SELECT COUNT(f) FROM Food f WHERE f.id = :id", Long.class)
                    .setParameter("id", id)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    @Override
    public long countByCategoryId(String categoryId) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery("SELECT COUNT(f) FROM Food f WHERE f.category.id = :categoryId", Long.class)
                    .setParameter("categoryId", categoryId)
                    .getSingleResult();
            return count != null ? count : 0L;
        }
    }

    @Override
    public Food save(Food food) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(food);
            tx.commit();
            return food;
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public Food update(Food food) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Food merged = em.merge(food);
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

    @Override
    public void deleteById(String id) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Food food = em.find(Food.class, id);
            if (food != null) {
                em.remove(food);
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

    @Override
    public String generateNextId() {
        return "MA" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
