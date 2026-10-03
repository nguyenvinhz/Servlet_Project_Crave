package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.entity.Promotion;
import com.foodordering.enums.DiscountType;
import com.foodordering.enums.PromotionStatus;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository xử lý các thao tác cơ sở dữ liệu đối với bảng promotion và thủ tục sp_calculate_discount.
 */
public class PromotionRepository {

    /**
     * Tìm khuyến mãi theo mã code duy nhất (ví dụ: WELCOME10, SAVE20K).
     */
    public Promotion findByCode(String code) {
        if (code == null) return null;
        String sql = "SELECT promotion_id, code, name, discount_type, discount_value, " +
                     "       minimum_order_value, maximum_discount, start_at, end_at, status, created_at, updated_at " +
                     "FROM promotion WHERE code = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, code.trim());
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapResultSetToPromotion(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi tìm khuyến mãi theo code: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Tìm khuyến mãi theo promotion_id.
     */
    public Promotion findById(String promotionId) {
        if (promotionId == null) return null;
        String sql = "SELECT promotion_id, code, name, discount_type, discount_value, " +
                     "       minimum_order_value, maximum_discount, start_at, end_at, status, created_at, updated_at " +
                     "FROM promotion WHERE promotion_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, promotionId.trim());
            rs = ps.executeQuery();
            if (rs.next()) {
                return mapResultSetToPromotion(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi tìm khuyến mãi theo ID: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Lấy danh sách tất cả các mã khuyến mãi đang trong thời gian hiệu lực và có status = 'ACTIVE'.
     */
    public List<Promotion> findAllActive(LocalDateTime currentTime) {
        List<Promotion> promotions = new ArrayList<>();
        String sql = "SELECT promotion_id, code, name, discount_type, discount_value, " +
                     "       minimum_order_value, maximum_discount, start_at, end_at, status, created_at, updated_at " +
                     "FROM promotion " +
                     "WHERE status = 'ACTIVE' AND ? BETWEEN start_at AND end_at " +
                     "ORDER BY minimum_order_value ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            LocalDateTime checkTime = currentTime != null ? currentTime : LocalDateTime.now();
            ps.setTimestamp(1, Timestamp.valueOf(checkTime));
            rs = ps.executeQuery();
            while (rs.next()) {
                promotions.add(mapResultSetToPromotion(rs));
            }
            return promotions;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi lấy danh sách khuyến mãi đang hoạt động: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Gọi Stored Procedure sp_calculate_discount trong MySQL database để tính số tiền giảm chính xác.
     */
    public BigDecimal calculateDiscountViaDatabase(String promotionId, BigDecimal subtotal, LocalDateTime orderTime) {
        String callSql = "{CALL sp_calculate_discount(?, ?, ?, ?)}";
        Connection conn = null;
        CallableStatement cs = null;
        try {
            conn = DatabaseConfig.getConnection();
            cs = conn.prepareCall(callSql);
            cs.setString(1, promotionId);
            cs.setTimestamp(2, Timestamp.valueOf(orderTime != null ? orderTime : LocalDateTime.now()));
            cs.setBigDecimal(3, subtotal != null ? subtotal : BigDecimal.ZERO);
            cs.registerOutParameter(4, Types.DECIMAL);

            cs.execute();
            BigDecimal discount = cs.getBigDecimal(4);
            return discount != null ? discount : BigDecimal.ZERO;
        } catch (SQLException e) {
            // Nếu stored procedure chưa được tạo trong DB local, fallback tính toán Java độc lập
            return null;
        } finally {
            DatabaseConfig.closeQuietly(cs);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    private Promotion mapResultSetToPromotion(ResultSet rs) throws SQLException {
        Promotion p = new Promotion();
        p.setPromotionId(rs.getString("promotion_id"));
        p.setCode(rs.getString("code"));
        p.setName(rs.getString("name"));
        p.setDiscountType(DiscountType.fromString(rs.getString("discount_type")));
        p.setDiscountValue(rs.getBigDecimal("discount_value"));
        p.setMinimumOrderValue(rs.getBigDecimal("minimum_order_value"));
        p.setMaximumDiscount(rs.getBigDecimal("maximum_discount"));
        Timestamp start = rs.getTimestamp("start_at");
        if (start != null) p.setStartAt(start.toLocalDateTime());
        Timestamp end = rs.getTimestamp("end_at");
        if (end != null) p.setEndAt(end.toLocalDateTime());
        p.setStatus(PromotionStatus.fromString(rs.getString("status")));
        Timestamp c = rs.getTimestamp("created_at");
        if (c != null) p.setCreatedAt(c.toLocalDateTime());
        Timestamp u = rs.getTimestamp("updated_at");
        if (u != null) p.setUpdatedAt(u.toLocalDateTime());
        return p;
    }
}
