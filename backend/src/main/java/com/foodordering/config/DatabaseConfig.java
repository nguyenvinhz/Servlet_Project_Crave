package com.foodordering.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

public final class DatabaseConfig {

    public static final String PERSISTENCE_UNIT = "cravePU";
    private static volatile EntityManagerFactory entityManagerFactory;

    private DatabaseConfig() {
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        EntityManagerFactory current = entityManagerFactory;
        if (current == null) {
            synchronized (DatabaseConfig.class) {
                current = entityManagerFactory;
                if (current == null) {
                    current = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT, properties());
                    entityManagerFactory = current;
                }
            }
        }
        return current;
    }

    public static void verifyConnection() {
        try (EntityManager entityManager = getEntityManagerFactory().createEntityManager()) {
            entityManager.createNativeQuery("SELECT 1", Integer.class).getSingleResult();
        }
    }

    public static void close() {
        EntityManagerFactory current = entityManagerFactory;
        if (current != null && current.isOpen()) {
            current.close();
        }
        entityManagerFactory = null;
    }

    static Map<String, Object> properties() {
        String jdbcUrl = env("DB_URL",
                "jdbc:mysql://localhost:3306/crave?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8");
        String username = env("DB_USER", "crave_app");
        String password = env("DB_PASSWORD", "");

        Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");
        properties.put("jakarta.persistence.jdbc.url", jdbcUrl);
        properties.put("jakarta.persistence.jdbc.user", username);
        properties.put("jakarta.persistence.jdbc.password", password);
        properties.put("hibernate.connection.provider_class",
                "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        properties.put("hibernate.hikari.jdbcUrl", jdbcUrl);
        properties.put("hibernate.hikari.username", username);
        properties.put("hibernate.hikari.password", password);
        properties.put("hibernate.hikari.maximumPoolSize", env("DB_POOL_MAX_SIZE", "10"));
        properties.put("hibernate.hikari.minimumIdle", env("DB_POOL_MIN_IDLE", "1"));
        properties.put("hibernate.hikari.connectionTimeout", env("DB_CONNECTION_TIMEOUT_MS", "5000"));
        properties.put("hibernate.hikari.validationTimeout", env("DB_VALIDATION_TIMEOUT_MS", "3000"));
        return properties;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
