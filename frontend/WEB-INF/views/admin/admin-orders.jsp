<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Quản lý đơn hàng (Admin) | Crave</title>
</head>
<body>
    <h1>Quản lý đơn hàng (Admin)</h1>
    <p>Chức năng Admin xử lý đơn hàng sẽ được phát triển trong Ngày 2.</p>

    <!-- Khung bảng danh sách đơn hàng -->
    <table border="1">
        <thead>
            <tr>
                <th>Mã Đơn</th>
                <th>Khách Hàng</th>
                <th>Tổng Tiền</th>
                <th>Trạng Thái</th>
                <th>Thao Tác</th>
            </tr>
        </thead>
        <tbody id="ordersTableBody">
            <!-- Dữ liệu placeholder -->
            <tr>
                <td>ORD-12345</td>
                <td>Nguyen Van A</td>
                <td>150000</td>
                <td>PENDING_CONFIRMATION</td>
                <td>
                    <button onclick="showUpdateStatusModal('ORD-12345')">Cập nhật</button>
                </td>
            </tr>
        </tbody>
    </table>

    <!-- Khung cập nhật trạng thái đơn hàng -->
    <div id="updateStatusSection" style="margin-top:20px; padding:10px; border:1px solid #ccc;">
        <h3>Cập nhật trạng thái đơn hàng: <span id="currentOrderId"></span></h3>
        <label>Trạng thái mới:</label>
        <select id="newStatus">
            <option value="PENDING_CONFIRMATION">Chờ xác nhận</option>
            <option value="PREPARING">Đang chuẩn bị</option>
            <option value="DELIVERING">Đang giao hàng</option>
            <option value="COMPLETED">Hoàn tất</option>
            <option value="CANCELLED">Đã hủy</option>
        </select><br/>
        
        <label>Ghi chú:</label>
        <input type="text" id="statusNote" /><br/>
        
        <button onclick="updateOrderStatus()">Xác nhận (JSON)</button>
    </div>

    <script>
        let currentOrderToUpdate = '';

        function showUpdateStatusModal(orderId) {
            currentOrderToUpdate = orderId;
            document.getElementById('currentOrderId').innerText = orderId;
        }

        function updateOrderStatus() {
            if (!currentOrderToUpdate) {
                alert('Vui lòng chọn đơn hàng cần cập nhật');
                return;
            }

            const data = {
                status: document.getElementById('newStatus').value,
                note: document.getElementById('statusNote').value
            };

            fetch(`${pageContext.request.contextPath}/api/admin/orders/${currentOrderToUpdate}/status`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(data)
            }).then(res => {
                if (res.ok) {
                    alert('Cập nhật thành công!');
                } else {
                    alert('Cập nhật thất bại.');
                }
            }).catch(err => console.error('Error:', err));
        }
    </script>
</body>
</html>
