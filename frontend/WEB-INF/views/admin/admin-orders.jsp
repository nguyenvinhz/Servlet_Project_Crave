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

    <script>
        // Note: Cần có endpoint /api/admin/orders (GET) để lấy tất cả đơn
        // Tạm thời mockup vì OrderService chưa có get all orders
        document.addEventListener('DOMContentLoaded', async () => {
            const listEl = document.getElementById('adminOrderList');
            try {
                // Mock endpoint or real if implemented later
                const res = await fetch('/crave/api/orders'); // Fetch temporarily from customer api
                const data = await res.json();
                
                if (res.ok && data.success) {
                    const orders = data.data;
                    if (orders.length === 0) {
                        listEl.innerHTML = '<tr><td colspan="6">Không có đơn hàng.</td></tr>';
                        return;
                    }
                    
                    listEl.innerHTML = orders.map(o => `
                        <tr>
                            <td>#${o.orderId}</td>
                            <td>${o.receiverName}</td>
                            <td>${new Date(o.orderedAt).toLocaleString('vi-VN')}</td>
                            <td>${o.totalAmount ? o.totalAmount.toLocaleString('vi-VN') : 0} đ</td>
                            <td>
                                <select id="status_${o.orderId}" class="status-select">
                                    <option value="PENDING_CONFIRMATION" ${o.status==='PENDING_CONFIRMATION'?'selected':''}>Chờ xác nhận</option>
                                    <option value="PREPARING" ${o.status==='PREPARING'?'selected':''}>Đang chuẩn bị</option>
                                    <option value="DELIVERING" ${o.status==='DELIVERING'?'selected':''}>Đang giao</option>
                                    <option value="COMPLETED" ${o.status==='COMPLETED'?'selected':''}>Hoàn tất</option>
                                    <option value="CANCELLED" ${o.status==='CANCELLED'?'selected':''}>Đã hủy</option>
                                </select>
                            </td>
                            <td>
                                <button class="btn-update" onclick="updateStatus('${o.orderId}')">Cập nhật</button>
                            </td>
                        </tr>
                    `).join('');
                }
            } catch(e) {}
        });

        async function updateStatus(orderId) {
            const status = document.getElementById('status_' + orderId).value;
            const payload = { status: status, note: "Admin cập nhật" };
            
            try {
                const res = await fetch('/crave/api/admin/orders/' + orderId, {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                const data = await res.json();
                if (res.ok && data.success) {
                    alert('Cập nhật thành công');
                } else {
                    alert('Lỗi: ' + data.message);
                }
            } catch (err) {
                alert('Lỗi kết nối');
            }
        }
    </script>
</body>
</html>
