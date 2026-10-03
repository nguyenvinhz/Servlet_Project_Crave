package com.foodordering.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Cấu hình kết nối cơ sở dữ liệu MySQL cho hệ thống Crave.
 * Cho phép ghi đè thông qua biến môi trường (DB_URL, DB_USER, DB_PASSWORD).
 */
public class DatabaseConfig {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/crave?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Fallback for older driver class name if needed
            try {
                Class.forName("com.mysql.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    private DatabaseConfig() {
    }

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("DB_URL");
        if (url == null || url.trim().isEmpty()) {
            url = System.getProperty("db.url", DEFAULT_URL);
        }

        String user = System.getenv("DB_USER");
        if (user == null || user.trim().isEmpty()) {
            user = System.getProperty("db.user", DEFAULT_USER);
        }

        String password = System.getenv("DB_PASSWORD");
        if (password == null) {
            password = System.getProperty("db.password", DEFAULT_PASSWORD);
        }

        return DriverManager.getConnection(url, user, password);
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
