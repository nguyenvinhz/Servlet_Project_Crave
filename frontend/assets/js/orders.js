document.addEventListener('DOMContentLoaded', async () => {
    const listEl = document.getElementById('orderList');
    try {
        const res = await fetch('/crave/api/orders');
        const data = await res.json();
        
        if (res.ok && data.success) {
            const orders = data.data;
            if (orders.length === 0) {
                listEl.innerHTML = '';
                const p = document.createElement('p');
                p.textContent = 'Bạn chưa có đơn hàng nào.';
                listEl.appendChild(p);
                return;
            }
            
            listEl.innerHTML = '';
            orders.forEach(o => {
                const card = document.createElement('div');
                card.className = 'order-card';
                
                const infoDiv = document.createElement('div');
                infoDiv.className = 'order-info';
                
                const h4 = document.createElement('h4');
                h4.textContent = 'Đơn hàng #' + o.orderId;
                
                const pDate = document.createElement('p');
                pDate.textContent = 'Ngày đặt: ' + new Date(o.orderedAt).toLocaleString('vi-VN');
                
                const pTotal = document.createElement('p');
                pTotal.innerHTML = 'Tổng tiền: <strong>' + (o.totalAmount || 0).toLocaleString('vi-VN') + ' đ</strong>';
                
                infoDiv.appendChild(h4);
                infoDiv.appendChild(pDate);
                infoDiv.appendChild(pTotal);
                
                const actionsDiv = document.createElement('div');
                actionsDiv.className = 'order-actions';
                actionsDiv.style.textAlign = 'right';
                
                const spanBadge = document.createElement('span');
                spanBadge.className = 'badge ' + o.status;
                spanBadge.textContent = formatStatus(o.status);
                
                const br1 = document.createElement('br');
                const br2 = document.createElement('br');
                
                const aView = document.createElement('a');
                aView.href = '/crave/orders/' + o.orderId;
                aView.className = 'btn-view';
                aView.textContent = 'Xem chi tiết';
                
                actionsDiv.appendChild(spanBadge);
                actionsDiv.appendChild(br1);
                actionsDiv.appendChild(br2);
                actionsDiv.appendChild(aView);
                
                card.appendChild(infoDiv);
                card.appendChild(actionsDiv);
                
                listEl.appendChild(card);
            });
        } else {
            listEl.innerHTML = '';
            const p = document.createElement('p');
            p.style.color = 'red';
            p.textContent = data.message || 'Lỗi tải đơn hàng.';
            listEl.appendChild(p);
        }
    } catch (err) {
        listEl.innerHTML = '';
        const p = document.createElement('p');
        p.style.color = 'red';
        p.textContent = 'Lỗi kết nối máy chủ.';
        listEl.appendChild(p);
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
