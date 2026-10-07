package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Category;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.NoResultException;

import java.util.List;
import java.util.Optional;

public class JpaCategoryRepository implements CategoryRepository {

    private final EntityManagerFactory emf;

    public JpaCategoryRepository() {
        this(DatabaseConfig.getEntityManagerFactory());
    }

    public JpaCategoryRepository(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public List<Category> findAll() {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery("SELECT c FROM Category c ORDER BY c.id ASC", Category.class)
                    .getResultList();
        }
    }

    @Override
    public Optional<Category> findById(String id) {
        try (EntityManager em = emf.createEntityManager()) {
            Category category = em.find(Category.class, id);
            return Optional.ofNullable(category);
        }
    }

    @Override
    public Optional<Category> findByName(String name) {
        try (EntityManager em = emf.createEntityManager()) {
            try {
                Category category = em.createQuery("SELECT c FROM Category c WHERE LOWER(c.name) = LOWER(:name)", Category.class)
                        .setParameter("name", name)
                        .getSingleResult();
                return Optional.of(category);
            } catch (NoResultException e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public boolean existsById(String id) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery("SELECT COUNT(c) FROM Category c WHERE c.id = :id", Long.class)
                    .setParameter("id", id)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    @Override
    public boolean existsByName(String name) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery("SELECT COUNT(c) FROM Category c WHERE LOWER(c.name) = LOWER(:name)", Long.class)
                    .setParameter("name", name)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    @Override
    public boolean existsByNameAndIdNot(String name, String id) {
        try (EntityManager em = emf.createEntityManager()) {
            Long count = em.createQuery(
                    "SELECT COUNT(c) FROM Category c WHERE LOWER(c.name) = LOWER(:name) AND c.id <> :id", Long.class)
                    .setParameter("name", name)
                    .setParameter("id", id)
                    .getSingleResult();
            return count != null && count > 0;
        }
    }

    @Override
    public Category save(Category category) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(category);
            tx.commit();
            return category;
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
    public Category update(Category category) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Category merged = em.merge(category);
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
            Category category = em.find(Category.class, id);
            if (category != null) {
                em.remove(category);
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
        try (EntityManager em = emf.createEntityManager()) {
            List<String> ids = em.createQuery("SELECT c.id FROM Category c WHERE c.id LIKE 'DM%' ORDER BY c.id DESC", String.class)
                    .setMaxResults(1)
                    .getResultList();
            if (ids.isEmpty()) {
                return "DM01";
            }
            String lastId = ids.get(0);
            try {
                int num = Integer.parseInt(lastId.substring(2));
                return String.format("DM%02d", num + 1);
            } catch (NumberFormatException e) {
                return "DM" + System.currentTimeMillis() % 10000;
            }
        }
    }
}
