package com.foodordering.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình cơ sở dữ liệu và quản lý EntityManagerFactory cho JPA (non-Spring Boot).
 */
public final class DatabaseConfig {

    public static final String PERSISTENCE_UNIT = "cravePU";
    private static volatile EntityManagerFactory entityManagerFactory;

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/crave?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private DatabaseConfig() {
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        EntityManagerFactory current = entityManagerFactory;
        if (current == null) {
            synchronized (DatabaseConfig.class) {
                current = entityManagerFactory;
                if (current == null) {
                    try {
                        current = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT, getJpaProperties());
                        entityManagerFactory = current;
                    } catch (Exception e) {
                        System.err.println("Chưa thể khởi tạo EntityManagerFactory: " + e.getMessage());
                    }
                }
            }
        }
        return current;
    }

    public static EntityManager getEntityManager() {
        EntityManagerFactory emf = getEntityManagerFactory();
        return emf != null ? emf.createEntityManager() : null;
    }

    public static void verifyConnection() {
        EntityManager em = getEntityManager();
        if (em != null) {
            try {
                em.createNativeQuery("SELECT 1").getSingleResult();
            } finally {
                em.close();
            }
        }
    }

    public static void close() {
        EntityManagerFactory current = entityManagerFactory;
        if (current != null && current.isOpen()) {
            current.close();
        }
        entityManagerFactory = null;
    }

    public static Map<String, Object> getJpaProperties() {
        Map<String, Object> props = new HashMap<>();
        String url = getDbUrl();
        String user = getDbUser();
        String password = getDbPassword();

        props.put("jakarta.persistence.jdbc.driver", "com.mysql.cj.jdbc.Driver");
        props.put("jakarta.persistence.jdbc.url", url);
        props.put("jakarta.persistence.jdbc.user", user);
        props.put("jakarta.persistence.jdbc.password", password);
        return props;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getDbUrl(), getDbUser(), getDbPassword());
    }

    public static String getDbUrl() {
        String url = System.getenv("DB_URL");
        return (url != null && !url.trim().isEmpty()) ? url.trim() : DEFAULT_URL;
    }

    public static String getDbUser() {
        String user = System.getenv("DB_USER");
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("CRAVE_DB_USER");
        }
        return (user != null && !user.trim().isEmpty()) ? user.trim() : DEFAULT_USER;
    }

    public static String getDbPassword() {
        String pass = System.getenv("DB_PASSWORD");
        if (pass == null) {
            pass = System.getenv("CRAVE_DB_PASSWORD");
        }
        return pass != null ? pass : DEFAULT_PASSWORD;
    }

    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
