<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Lịch sử đơn hàng | Crave</title>
    <link rel="stylesheet" href="/crave/assets/css/styles.css">
    <style>
        .orders-container { max-width: 800px; margin: 40px auto; }
        .order-card { background: #fff; border: 1px solid #ddd; border-radius: 8px; padding: 15px; margin-bottom: 15px; display: flex; justify-content: space-between; align-items: center; }
        .order-info h4 { margin: 0 0 5px 0; }
        .order-info p { margin: 2px 0; color: #555; font-size: 14px; }
        .badge { padding: 5px 10px; border-radius: 4px; font-size: 12px; font-weight: bold; }
        .badge.PENDING_CONFIRMATION { background: #f39c12; color: #fff; }
        .badge.PREPARING { background: #3498db; color: #fff; }
        .badge.DELIVERING { background: #9b59b6; color: #fff; }
        .badge.COMPLETED { background: #2ecc71; color: #fff; }
        .badge.CANCELLED { background: #e74c3c; color: #fff; }
        .btn-view { padding: 8px 15px; background: #ecf0f1; text-decoration: none; color: #333; border-radius: 4px; font-size: 14px; }
        .btn-view:hover { background: #bdc3c7; }
    </style>
</head>
<body>
    <div class="orders-container">
        <h2>Lịch sử đơn hàng của bạn</h2>
        <div id="orderList">
            <p>Đang tải dữ liệu...</p>
        </div>
    </div>

    <script src="/crave/assets/js/orders.js"></script>
</body>
</html>
