package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Cart;
import com.foodordering.utils.IdGenerator;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;

/**
 * Repository xử lý các thao tác cơ sở dữ liệu đối với bảng cart và view v_cart_summary.
 */
public class CartRepository {

    /**
     * Tìm giỏ hàng theo mã khách hàng.
     */
    public Cart findByCustomerId(String customerId) {
        String sql = "SELECT cart_id, customer_id, created_at, updated_at FROM cart WHERE customer_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, customerId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapResultSetToCart(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi tìm giỏ hàng theo customerId: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Tìm giỏ hàng theo ID.
     */
    public Cart findById(String cartId) {
        String sql = "SELECT cart_id, customer_id, created_at, updated_at FROM cart WHERE cart_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, cartId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapResultSetToCart(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi tìm giỏ hàng theo cartId: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Tạo giỏ hàng mới cho khách hàng nếu chưa có.
     */
    public Cart createCart(String customerId) {
        String sql = "INSERT INTO cart (cart_id, customer_id, created_at, updated_at) VALUES (?, ?, NOW(), NOW())";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DatabaseConfig.getConnection();
            String newCartId = getNextCartId(conn);
            ps = conn.prepareStatement(sql);
            ps.setString(1, newCartId);
            ps.setString(2, customerId);
            ps.executeUpdate();

            Cart cart = new Cart();
            cart.setCartId(newCartId);
            cart.setCustomerId(customerId);
            cart.setCreatedAt(LocalDateTime.now());
            cart.setUpdatedAt(LocalDateTime.now());
            return cart;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi tạo mới giỏ hàng: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Đọc tổng tạm tính (subtotal) của giỏ hàng từ view v_cart_summary trên server.
     */
    public BigDecimal getCartSubtotal(String cartId) {
        String sql = "SELECT subtotal FROM v_cart_summary WHERE cart_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, cartId);
            rs = ps.executeQuery();
            if (rs.next()) {
                BigDecimal subtotal = rs.getBigDecimal("subtotal");
                return subtotal != null ? subtotal : BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi lấy subtotal giỏ hàng: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Sinh ID giỏ hàng tuần tự dạng GH01, GH02... hoặc fallback ngẫu nhiên nếu trùng.
     */
    private String getNextCartId(Connection conn) throws SQLException {
        String countSql = "SELECT cart_id FROM cart ORDER BY cart_id DESC LIMIT 1";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(countSql)) {
            if (rs.next()) {
                String lastId = rs.getString("cart_id");
                if (lastId != null && lastId.startsWith("GH")) {
                    try {
                        int num = Integer.parseInt(lastId.substring(2));
                        return String.format("GH%02d", num + 1);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return IdGenerator.generateCartId();
    }

    private Cart mapResultSetToCart(ResultSet rs) throws SQLException {
        Cart cart = new Cart();
        cart.setCartId(rs.getString("cart_id"));
        cart.setCustomerId(rs.getString("customer_id"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) cart.setCreatedAt(created.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) cart.setUpdatedAt(updated.toLocalDateTime());
        return cart;
    }
}
