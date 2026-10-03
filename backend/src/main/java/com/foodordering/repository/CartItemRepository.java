package com.foodordering.repository;

import com.foodordering.config.DatabaseConfig;
import com.foodordering.dto.CartItemDto;
import com.foodordering.dto.CartItemOptionDto;
import com.foodordering.entity.CartItem;
import com.foodordering.utils.IdGenerator;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Repository xử lý các thao tác cơ sở dữ liệu đối với bảng cart_item và cart_item_option.
 */
public class CartItemRepository {

    /**
     * Lấy toàn bộ danh sách món ăn trong giỏ hàng kèm theo tùy chọn và thông tin món ăn.
     */
    public List<CartItemDto> findItemsByCartId(String cartId) {
        List<CartItemDto> items = new ArrayList<>();
        String sql = "SELECT ci.cart_item_id, ci.cart_id, ci.food_id, ci.quantity, ci.note, " +
                     "       f.name AS food_name, f.image_url, f.price AS base_price " +
                     "FROM cart_item ci " +
                     "JOIN food f ON f.food_id = ci.food_id " +
                     "WHERE ci.cart_id = ? " +
                     "ORDER BY ci.created_at ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, cartId);
            rs = ps.executeQuery();
            while (rs.next()) {
                CartItemDto item = new CartItemDto();
                item.setCartItemId(rs.getString("cart_item_id"));
                item.setCartId(rs.getString("cart_id"));
                item.setFoodId(rs.getString("food_id"));
                item.setFoodName(rs.getString("food_name"));
                item.setImageUrl(rs.getString("image_url"));
                item.setBasePrice(rs.getBigDecimal("base_price"));
                item.setQuantity(rs.getInt("quantity"));
                item.setNote(rs.getString("note"));

                // Lấy danh sách options cho item này
                item.setOptions(findOptionsByCartItemId(conn, item.getCartItemId()));
                item.calculateTotals();
                items.add(item);
            }
            return items;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi lấy danh sách món trong giỏ hàng: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Lấy các tùy chọn đã chọn cho một mục giỏ hàng.
     */
    public List<CartItemOptionDto> findOptionsByCartItemId(Connection conn, String cartItemId) throws SQLException {
        List<CartItemOptionDto> options = new ArrayList<>();
        String sql = "SELECT fo.option_id, fo.name, fo.option_type, fo.extra_price " +
                     "FROM cart_item_option cio " +
                     "JOIN food_option fo ON fo.option_id = cio.option_id " +
                     "WHERE cio.cart_item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cartItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CartItemOptionDto opt = new CartItemOptionDto();
                    opt.setOptionId(rs.getString("option_id"));
                    opt.setName(rs.getString("name"));
                    opt.setOptionType(rs.getString("option_type"));
                    opt.setExtraPrice(rs.getBigDecimal("extra_price"));
                    options.add(opt);
                }
            }
        }
        return options;
    }

    /**
     * Tìm món trong giỏ theo ID.
     */
    public CartItem findById(String cartItemId) {
        String sql = "SELECT cart_item_id, cart_id, food_id, quantity, note, created_at, updated_at " +
                     "FROM cart_item WHERE cart_item_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, cartItemId);
            rs = ps.executeQuery();
            if (rs.next()) {
                CartItem item = new CartItem();
                item.setCartItemId(rs.getString("cart_item_id"));
                item.setCartId(rs.getString("cart_id"));
                item.setFoodId(rs.getString("food_id"));
                item.setQuantity(rs.getInt("quantity"));
                item.setNote(rs.getString("note"));
                Timestamp c = rs.getTimestamp("created_at");
                if (c != null) item.setCreatedAt(c.toLocalDateTime());
                Timestamp u = rs.getTimestamp("updated_at");
                if (u != null) item.setUpdatedAt(u.toLocalDateTime());
                return item;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi tìm cart item: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Kiểm tra món ăn có tồn tại và đang AVAILABLE hay không.
     */
    public boolean isFoodAvailable(String foodId) {
        String sql = "SELECT status FROM food WHERE food_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, foodId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return "AVAILABLE".equalsIgnoreCase(rs.getString("status"));
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi kiểm tra trạng thái món ăn: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Xác thực các option có thuộc món ăn foodId và đang ACTIVE hay không.
     * Trả về danh sách các option hợp lệ.
     */
    public List<String> getValidOptionIdsForFood(String foodId, List<String> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> validIds = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT option_id FROM food_option WHERE food_id = ? AND status = 'ACTIVE' AND option_id IN (");
        for (int i = 0; i < optionIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql.toString());
            ps.setString(1, foodId);
            for (int i = 0; i < optionIds.size(); i++) {
                ps.setString(i + 2, optionIds.get(i));
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                validIds.add(rs.getString("option_id"));
            }
            return validIds;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi kiểm tra tùy chọn món: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Tìm món đã có trong giỏ có cùng foodId và tập hợp optionIds y hệt nhau.
     * Nếu tìm thấy, hệ thống sẽ cộng dồn số lượng thay vì tạo dòng mới.
     */
    public CartItem findDuplicateItem(String cartId, String foodId, List<String> newOptionIds) {
        String sql = "SELECT cart_item_id, quantity, note FROM cart_item WHERE cart_id = ? AND food_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, cartId);
            ps.setString(2, foodId);
            rs = ps.executeQuery();

            Set<String> targetSet = new HashSet<>(newOptionIds != null ? newOptionIds : Collections.emptyList());

            while (rs.next()) {
                String existingItemId = rs.getString("cart_item_id");
                Set<String> existingOptions = getOptionIdsForItem(conn, existingItemId);
                if (existingOptions.equals(targetSet)) {
                    CartItem duplicate = new CartItem();
                    duplicate.setCartItemId(existingItemId);
                    duplicate.setCartId(cartId);
                    duplicate.setFoodId(foodId);
                    duplicate.setQuantity(rs.getInt("quantity"));
                    duplicate.setNote(rs.getString("note"));
                    return duplicate;
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi tìm món trùng trong giỏ: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(rs);
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    private Set<String> getOptionIdsForItem(Connection conn, String cartItemId) throws SQLException {
        Set<String> set = new HashSet<>();
        String sql = "SELECT option_id FROM cart_item_option WHERE cart_item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cartItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString("option_id"));
                }
            }
        }
        return set;
    }

    /**
     * Thêm món mới vào giỏ hàng và lưu các options trong một transaction duy nhất.
     */
    public void insertItemWithOptions(CartItem item, List<String> optionIds) {
        String insertItemSql = "INSERT INTO cart_item (cart_item_id, cart_id, food_id, quantity, note, created_at, updated_at) " +
                               "VALUES (?, ?, ?, ?, ?, NOW(), NOW())";
        String insertOptSql = "INSERT INTO cart_item_option (cart_item_id, option_id) VALUES (?, ?)";

        Connection conn = null;
        PreparedStatement psItem = null;
        PreparedStatement psOpt = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            if (item.getCartItemId() == null || item.getCartItemId().trim().isEmpty()) {
                item.setCartItemId(getNextCartItemId(conn));
            }

            psItem = conn.prepareStatement(insertItemSql);
            psItem.setString(1, item.getCartItemId());
            psItem.setString(2, item.getCartId());
            psItem.setString(3, item.getFoodId());
            psItem.setInt(4, item.getQuantity());
            psItem.setString(5, item.getNote());
            psItem.executeUpdate();

            if (optionIds != null && !optionIds.isEmpty()) {
                psOpt = conn.prepareStatement(insertOptSql);
                for (String optId : optionIds) {
                    psOpt.setString(1, item.getCartItemId());
                    psOpt.setString(2, optId);
                    psOpt.addBatch();
                }
                psOpt.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw new RuntimeException("Lỗi khi thêm món vào giỏ hàng: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(psOpt);
            DatabaseConfig.closeQuietly(psItem);
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
                DatabaseConfig.closeQuietly(conn);
            }
        }
    }

    /**
     * Cập nhật số lượng món trong giỏ hàng.
     */
    public void updateQuantity(String cartItemId, int newQuantity) {
        String sql = "UPDATE cart_item SET quantity = ?, updated_at = NOW() WHERE cart_item_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setInt(1, newQuantity);
            ps.setString(2, cartItemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi cập nhật số lượng món: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Cập nhật ghi chú món trong giỏ hàng.
     */
    public void updateNote(String cartItemId, String note) {
        String sql = "UPDATE cart_item SET note = ?, updated_at = NOW() WHERE cart_item_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, note);
            ps.setString(2, cartItemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi cập nhật ghi chú món: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Xóa một món khỏi giỏ hàng.
     */
    public void deleteItem(String cartItemId) {
        String sql = "DELETE FROM cart_item WHERE cart_item_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, cartItemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi xóa món khỏi giỏ hàng: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    /**
     * Xóa toàn bộ món trong giỏ hàng của cartId.
     */
    public void clearCart(String cartId) {
        String sql = "DELETE FROM cart_item WHERE cart_id = ?";
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = DatabaseConfig.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, cartId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi làm sạch giỏ hàng: " + e.getMessage(), e);
        } finally {
            DatabaseConfig.closeQuietly(ps);
            DatabaseConfig.closeQuietly(conn);
        }
    }

    private String getNextCartItemId(Connection conn) throws SQLException {
        String sql = "SELECT cart_item_id FROM cart_item ORDER BY cart_item_id DESC LIMIT 1";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                String lastId = rs.getString("cart_item_id");
                if (lastId != null && lastId.startsWith("CTGH")) {
                    try {
                        int num = Integer.parseInt(lastId.substring(4));
                        return String.format("CTGH%02d", num + 1);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return IdGenerator.generateCartItemId();
    }
}
