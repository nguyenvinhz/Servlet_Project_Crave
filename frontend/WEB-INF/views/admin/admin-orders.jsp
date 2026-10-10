<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Quản lý đơn hàng (Admin) | Crave</title>
    <link rel="stylesheet" href="/crave/assets/css/styles.css">
    <style>
        .admin-container { max-width: 1000px; margin: 40px auto; }
        table { width: 100%; border-collapse: collapse; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        th, td { padding: 12px; border-bottom: 1px solid #ddd; text-align: left; }
        th { background: #f4f4f4; }
        select.status-select { padding: 5px; }
        .btn-update { padding: 5px 10px; background: #3498db; color: #fff; border: none; cursor: pointer; border-radius: 3px; }
        .btn-update:hover { background: #2980b9; }
    </style>
</head>
<body>
    <div class="admin-container">
        <h2>Quản lý đơn hàng</h2>
        <table>
            <thead>
                <tr>
                    <th>Mã Đơn</th>
                    <th>Khách Hàng</th>
                    <th>Ngày Đặt</th>
                    <th>Tổng Tiền</th>
                    <th>Trạng Thái</th>
                    <th>Thao Tác</th>
                </tr>
            </thead>
            <tbody id="adminOrderList">
                <tr><td colspan="6">Đang tải...</td></tr>
            </tbody>
        </table>
    </div>

    <script src="/crave/assets/js/admin-orders.js"></script>
</body>
</html>
