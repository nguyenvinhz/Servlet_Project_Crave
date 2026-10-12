<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Chi tiết đơn hàng | Crave</title>
    <link rel="stylesheet" href="/crave/assets/css/styles.css">
    <style>
        .detail-container { max-width: 800px; margin: 40px auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .order-header { border-bottom: 1px solid #eee; padding-bottom: 15px; margin-bottom: 15px; }
        .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px; }
        .item-list { border-top: 1px solid #eee; padding-top: 15px; }
        .item-row { display: flex; justify-content: space-between; margin-bottom: 10px; border-bottom: 1px dashed #eee; padding-bottom: 10px; }
        .item-details small { color: #777; display: block; }
        .totals { margin-top: 20px; text-align: right; border-top: 1px solid #eee; padding-top: 15px; }
        .btn-back { display: inline-block; margin-bottom: 20px; text-decoration: none; color: #3498db; }
    </style>
</head>
<body>
    <div class="detail-container">
        <a href="/crave/orders" class="btn-back">← Quay lại danh sách</a>
        <div id="orderContent">Đang tải...</div>
    </div>

    <script src="/crave/assets/js/order-detail.js"></script>
</body>
</html>
