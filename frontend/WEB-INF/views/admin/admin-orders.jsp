<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Manage Orders" scope="request"/>
<%@ include file="../components/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/assets/css/order.css'/>">

<main class="page order-page order-theme">
    <div class="tracking-header" style="margin-bottom: 2rem;">
        <h1>Manage Orders (Admin)</h1>
        <p>Update order status and handle customer requests.</p>
    </div>

    <!-- Khung bảng danh sách đơn hàng -->
    <div class="card" style="padding: 0; overflow: hidden;">
        <table class="admin-table">
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
                    <td><span class="order-status-badge badge-pending">PENDING_CONFIRMATION</span></td>
                    <td>
                        <button class="admin-action-btn" onclick="showUpdateStatusModal('ORD-12345')">Cập nhật</button>
                    </td>
                </tr>
            </tbody>
        </table>
    </div>

    <!-- Khung cập nhật trạng thái đơn hàng -->
    <div id="updateStatusSection" class="card" style="margin-top: 2rem; display: none;">
        <h3>Cập nhật đơn hàng: <span id="currentOrderId"></span></h3>

        <div class="field-grid" style="margin-top: 1rem;">
            <div>
                <label>Trạng thái mới:</label>
                <select id="newStatus">
                    <option value="PENDING_CONFIRMATION">Chờ xác nhận</option>
                    <option value="PREPARING">Đang chuẩn bị</option>
                    <option value="DELIVERING">Đang giao hàng</option>
                    <option value="COMPLETED">Hoàn tất</option>
                    <option value="CANCELLED">Đã hủy</option>
                </select>
            </div>
            <div>
                <label>Ghi chú (nhân viên):</label>
                <input type="text" id="statusNote" placeholder="Lý do hủy hoặc ghi chú nội bộ" />
            </div>
        </div>

        <button class="btn-primary" onclick="updateOrderStatus()">Xác nhận (JSON)</button>
    </div>
</main>

<script>
    let currentOrderToUpdate = '';

    function showUpdateStatusModal(orderId) {
        currentOrderToUpdate = orderId;
        document.getElementById('currentOrderId').innerText = orderId;
        document.getElementById('updateStatusSection').style.display = 'block';
        document.getElementById('updateStatusSection').scrollIntoView({ behavior: 'smooth' });
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

        fetch(`${pageContext.request.contextPath}/api/admin/orders/` + currentOrderToUpdate + `/status`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        }).then(res => {
            if (res.ok) {
                alert('Cập nhật thành công!');
                document.getElementById('updateStatusSection').style.display = 'none';
            } else {
                alert('Cập nhật thất bại.');
            }
        }).catch(err => console.error('Error:', err));
    }
</script>

<%@ include file="../components/footer.jspf" %>
