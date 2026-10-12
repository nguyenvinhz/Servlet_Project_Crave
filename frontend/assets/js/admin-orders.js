document.addEventListener('DOMContentLoaded', () => {
    const listEl = document.getElementById('adminOrderList');
    const alertEl = document.getElementById('adminAlert');
    const refreshBtn = document.getElementById('refreshBtn');
    const API_BASE = document.body.dataset.contextPath || '';

    function showAlert(message, isSuccess = false) {
        alertEl.style.display = 'block';
        alertEl.style.background = isSuccess ? 'rgba(74, 222, 128, 0.15)' : 'rgba(239, 68, 68, 0.15)';
        alertEl.style.border = isSuccess ? '1px solid #4ade80' : '1px solid #f87171';
        alertEl.style.color = isSuccess ? '#4ade80' : '#f87171';
        alertEl.textContent = message;
        setTimeout(() => {
            alertEl.style.display = 'none';
        }, 5000);
    }

    async function loadOrders() {
        listEl.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 2.5rem; color: var(--text-muted);">Đang tải danh sách đơn hàng...</td></tr>';
        
        try {
            const res = await fetch(`${API_BASE}/api/admin/orders`, {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            });

            if (res.status === 401 || res.status === 403) {
                listEl.innerHTML = `
                    <tr><td colspan="7" style="text-align: center; padding: 3rem; color: #f87171;">
                        🔒 Bạn không có quyền truy cập trang này. Vui lòng đăng nhập với tài khoản nhân viên xử lý đơn (Order Staff).
                    </td></tr>
                `;
                return;
            }

            const data = await res.json();
            
            if (res.ok && data.success) {
                const orders = data.data || [];
                if (orders.length === 0) {
                    listEl.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 2.5rem; color: var(--text-muted);">Hiện tại không có đơn hàng nào trong hệ thống.</td></tr>';
                    return;
                }
                
                listEl.innerHTML = '';
                orders.forEach(o => {
                    const tr = document.createElement('tr');
                    
                    // Mã đơn
                    const tdId = document.createElement('td');
                    tdId.style.fontWeight = '700';
                    tdId.innerHTML = `<a href="${API_BASE}/orders/${encodeURIComponent(o.orderId)}" style="color: var(--accent); text-decoration: none;" title="Xem chi tiết">#${escapeHtml(o.orderId)}</a>`;
                    
                    // Khách hàng
                    const tdName = document.createElement('td');
                    tdName.innerHTML = `
                        <div style="font-weight: 600;">${escapeHtml(o.receiverName)}</div>
                        <div style="font-size: 0.8rem; color: var(--text-muted);">${escapeHtml(o.receiverPhone)}</div>
                    `;
                    
                    // Thời gian
                    const tdDate = document.createElement('td');
                    tdDate.style.fontSize = '0.85rem';
                    tdDate.textContent = new Date(o.orderedAt).toLocaleString('vi-VN');
                    
                    // Tổng tiền
                    const tdTotal = document.createElement('td');
                    tdTotal.style.fontWeight = '700';
                    tdTotal.textContent = (Number(o.totalAmount) || 0).toLocaleString('vi-VN') + ' đ';
                    
                    // Trạng thái đơn
                    const tdStatus = document.createElement('td');
                    const selectStatus = document.createElement('select');
                    selectStatus.id = 'status_' + o.orderId;
                    selectStatus.className = 'status-select';
                    
                    const orderOptions = [
                        { val: 'PENDING_CONFIRMATION', text: 'Chờ xác nhận' },
                        { val: 'PREPARING', text: 'Đang chuẩn bị' },
                        { val: 'DELIVERING', text: 'Đang giao hàng' },
                        { val: 'COMPLETED', text: 'Hoàn tất' },
                        { val: 'CANCELLED', text: 'Đã hủy' }
                    ];
                    orderOptions.forEach(opt => {
                        const optEl = document.createElement('option');
                        optEl.value = opt.val;
                        optEl.textContent = opt.text;
                        if (o.status === opt.val) optEl.selected = true;
                        selectStatus.appendChild(optEl);
                    });
                    tdStatus.appendChild(selectStatus);
                    
                    // Trạng thái thanh toán
                    const tdPayment = document.createElement('td');
                    const paymentId = o.payment?.paymentId;
                    const paymentStatus = o.payment?.status || 'PENDING';
                    
                    if (paymentId) {
                        const selectPay = document.createElement('select');
                        selectPay.id = 'pay_status_' + o.orderId;
                        selectPay.className = 'status-select';
                        
                        const payOptions = [
                            { val: 'PENDING', text: 'Chờ thanh toán' },
                            { val: 'SUCCESS', text: 'Đã thanh toán' },
                            { val: 'FAILED', text: 'Thất bại' },
                            { val: 'REFUNDED', text: 'Đã hoàn tiền' }
                        ];
                        payOptions.forEach(opt => {
                            const optEl = document.createElement('option');
                            optEl.value = opt.val;
                            optEl.textContent = opt.text;
                            if (paymentStatus === opt.val) optEl.selected = true;
                            selectPay.appendChild(optEl);
                        });
                        tdPayment.appendChild(selectPay);
                    } else {
                        tdPayment.innerHTML = '<span style="color: var(--text-muted); font-size: 0.85rem;">Không có</span>';
                    }
                    
                    // Thao tác
                    const tdAction = document.createElement('td');
                    tdAction.style.whiteSpace = 'nowrap';
                    
                    const btnUpdateStatus = document.createElement('button');
                    btnUpdateStatus.className = 'admin-action-btn';
                    btnUpdateStatus.textContent = 'Lưu đơn';
                    btnUpdateStatus.title = 'Cập nhật trạng thái đơn hàng';
                    btnUpdateStatus.onclick = () => updateOrderStatus(o.orderId);
                    tdAction.appendChild(btnUpdateStatus);
                    
                    if (paymentId) {
                        const btnUpdatePayment = document.createElement('button');
                        btnUpdatePayment.className = 'admin-action-btn';
                        btnUpdatePayment.style.marginLeft = '0.4rem';
                        btnUpdatePayment.style.background = '#2563eb';
                        btnUpdatePayment.textContent = 'Lưu TT';
                        btnUpdatePayment.title = 'Cập nhật trạng thái thanh toán';
                        btnUpdatePayment.onclick = () => updatePaymentStatus(paymentId, o.orderId);
                        tdAction.appendChild(btnUpdatePayment);
                    }
                    
                    tr.append(tdId, tdName, tdDate, tdTotal, tdStatus, tdPayment, tdAction);
                    listEl.appendChild(tr);
                });
            } else {
                listEl.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 2rem; color: #f87171;">${data?.error?.message || data?.message || 'Lỗi tải danh sách đơn hàng.'}</td></tr>`;
            }
        } catch (e) {
            listEl.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 2rem; color: #f87171;">Lỗi kết nối máy chủ.</td></tr>';
        }
    }

    async function updateOrderStatus(orderId) {
        const select = document.getElementById('status_' + orderId);
        if (!select) return;
        const newStatus = select.value;
        const payload = { status: newStatus, note: "Nhân viên cập nhật trạng thái" };
        
        try {
            const res = await fetch(`${API_BASE}/api/admin/orders/${encodeURIComponent(orderId)}/status`, {
                method: 'PATCH',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (res.ok && data.success) {
                showAlert(`Cập nhật trạng thái đơn #${orderId} thành công!`, true);
            } else {
                showAlert(`Lỗi cập nhật #${orderId}: ` + (data?.error?.message || data?.message || 'Không thể chuyển trạng thái.'));
                loadOrders(); // Tải lại để reset select về giá trị đúng
            }
        } catch (err) {
            showAlert('Lỗi kết nối khi cập nhật đơn hàng.');
        }
    }

    async function updatePaymentStatus(paymentId, orderId) {
        const select = document.getElementById('pay_status_' + orderId);
        if (!select) return;
        const newStatus = select.value;
        const payload = { status: newStatus, transactionRef: "ADMIN_UPDATE_" + Date.now() };
        
        try {
            const res = await fetch(`${API_BASE}/api/admin/payments/${encodeURIComponent(paymentId)}/status`, {
                method: 'PATCH',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (res.ok && data.success) {
                showAlert(`Cập nhật trạng thái thanh toán #${paymentId} thành công!`, true);
            } else {
                showAlert(`Lỗi cập nhật thanh toán: ` + (data?.error?.message || data?.message || 'Thất bại.'));
                loadOrders();
            }
        } catch (err) {
            showAlert('Lỗi kết nối khi cập nhật thanh toán.');
        }
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str).replace(/[&<>'"]/g, tag => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
        }[tag] || tag));
    }

    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadOrders);
    }

    loadOrders();
});
