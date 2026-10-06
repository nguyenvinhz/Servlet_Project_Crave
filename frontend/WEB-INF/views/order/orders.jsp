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

    <script>
        document.addEventListener('DOMContentLoaded', async () => {
            const listEl = document.getElementById('orderList');
            try {
                const res = await fetch('/crave/api/orders');
                const data = await res.json();
                
                if (res.ok && data.success) {
                    const orders = data.data;
                    if (orders.length === 0) {
                        listEl.innerHTML = '<p>Bạn chưa có đơn hàng nào.</p>';
                        return;
                    }
                    
                    listEl.innerHTML = orders.map(o => `
                        <div class="order-card">
                            <div class="order-info">
                                <h4>Đơn hàng #${o.orderId}</h4>
                                <p>Ngày đặt: ${new Date(o.orderedAt).toLocaleString('vi-VN')}</p>
                                <p>Tổng tiền: <strong>${o.totalAmount.toLocaleString('vi-VN')} đ</strong></p>
                            </div>
                            <div class="order-actions" style="text-align: right">
                                <span class="badge ${o.status}">${formatStatus(o.status)}</span>
                                <br><br>
                                <a href="/crave/orders/${o.orderId}" class="btn-view">Xem chi tiết</a>
                            </div>
                        </div>
                    `).join('');
                } else {
                    listEl.innerHTML = `<p style="color:red">${data.message || 'Lỗi tải đơn hàng.'}</p>`;
                }
            } catch (err) {
                listEl.innerHTML = '<p style="color:red">Lỗi kết nối máy chủ.</p>';
            }
        });

        function formatStatus(status) {
            const map = {
                'PENDING_CONFIRMATION': 'Chờ xác nhận',
                'PREPARING': 'Đang chuẩn bị',
                'DELIVERING': 'Đang giao hàng',
                'COMPLETED': 'Hoàn tất',
                'CANCELLED': 'Đã hủy'
            };
            return map[status] || status;
        }
    </script>
</body>
</html>
