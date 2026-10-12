document.addEventListener('DOMContentLoaded', async () => {
    const listEl = document.getElementById('orderList');
    const API_BASE = document.body.dataset.contextPath || '';

    try {
        const res = await fetch(`${API_BASE}/api/orders`, {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        });

        if (res.status === 401) {
            listEl.innerHTML = `
                <div class="card" style="padding: 2.5rem; text-align: center;">
                    <p style="color: var(--text-muted); margin-bottom: 1rem;">Vui lòng đăng nhập để xem lịch sử đơn hàng của bạn.</p>
                    <a href="${API_BASE}/auth/login?returnTo=${encodeURIComponent(window.location.pathname)}" class="btn-primary" style="display: inline-block; width: auto; margin: 0 auto; text-decoration: none;">Đăng nhập ngay</a>
                </div>
            `;
            return;
        }

        const data = await res.json();
        
        if (res.ok && data.success) {
            const orders = data.data || [];
            if (orders.length === 0) {
                listEl.innerHTML = `
                    <div class="card" style="padding: 3rem 1.5rem; text-align: center;">
                        <div style="font-size: 3rem; margin-bottom: 1rem;">🍽️</div>
                        <h3 style="margin-bottom: 0.5rem; font-size: 1.25rem;">Bạn chưa có đơn hàng nào</h3>
                        <p style="color: var(--text-muted); margin-bottom: 1.5rem; font-size: 0.95rem;">Hãy khám phá các món ngon đặc sắc tại Crave và đặt món ngay hôm nay!</p>
                        <a href="${API_BASE}/menu" class="btn-primary" style="display: inline-block; width: auto; margin: 0 auto; text-decoration: none; padding: 0.75rem 1.5rem;">Khám phá thực đơn</a>
                    </div>
                `;
                return;
            }
            
            listEl.innerHTML = '';
            orders.forEach(o => {
                const card = document.createElement('div');
                card.className = 'order-card';
                
                const infoDiv = document.createElement('div');
                infoDiv.className = 'order-card-info';
                
                const h3 = document.createElement('h3');
                h3.textContent = 'Đơn hàng #' + o.orderId;
                
                const pDate = document.createElement('p');
                pDate.textContent = 'Ngày đặt: ' + new Date(o.orderedAt).toLocaleString('vi-VN');
                
                const pFulfill = document.createElement('p');
                pFulfill.textContent = 'Hình thức: ' + (o.fulfillmentType === 'DELIVERY' ? 'Giao tận nơi' : 'Lấy tại quán');
                
                infoDiv.append(h3, pDate, pFulfill);
                
                const rightDiv = document.createElement('div');
                rightDiv.className = 'order-card-right';
                
                const priceDiv = document.createElement('div');
                priceDiv.className = 'price';
                priceDiv.textContent = (Number(o.totalAmount) || 0).toLocaleString('vi-VN') + ' đ';
                
                const badge = document.createElement('span');
                badge.className = 'order-status-badge ' + getBadgeClass(o.status);
                badge.textContent = formatStatus(o.status);
                
                const actionDiv = document.createElement('div');
                actionDiv.style.marginTop = '0.5rem';
                const aView = document.createElement('a');
                aView.href = `${API_BASE}/orders/${encodeURIComponent(o.orderId)}`;
                aView.style.color = 'var(--accent)';
                aView.style.fontWeight = '600';
                aView.style.fontSize = '0.85rem';
                aView.style.textDecoration = 'none';
                aView.textContent = 'Xem chi tiết →';
                actionDiv.appendChild(aView);
                
                rightDiv.append(priceDiv, badge, actionDiv);
                
                card.append(infoDiv, rightDiv);
                listEl.appendChild(card);
            });
        } else {
            listEl.innerHTML = `
                <div class="card" style="padding: 2rem; text-align: center; color: #f87171;">
                    <p>${data?.error?.message || data?.message || 'Không thể tải danh sách đơn hàng.'}</p>
                </div>
            `;
        }
    } catch (err) {
        listEl.innerHTML = `
            <div class="card" style="padding: 2rem; text-align: center; color: #f87171;">
                <p>Lỗi kết nối máy chủ. Vui lòng thử lại sau.</p>
            </div>
        `;
    }
});

function getBadgeClass(status) {
    const map = {
        'PENDING_CONFIRMATION': 'badge-pending',
        'PREPARING': 'badge-preparing',
        'DELIVERING': 'badge-delivering',
        'COMPLETED': 'badge-completed',
        'CANCELLED': 'badge-cancelled'
    };
    return map[status] || 'badge-pending';
}

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
