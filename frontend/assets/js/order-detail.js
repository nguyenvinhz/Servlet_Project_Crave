document.addEventListener('DOMContentLoaded', async () => {
    const container = document.getElementById('orderContent');
    const API_BASE = document.body.dataset.contextPath || '';
    
    // Lấy orderId từ cuối URL. Ví dụ /orders/DH01 hoặc /crave/orders/DH01
    const pathSegments = window.location.pathname.split('/').filter(Boolean);
    const orderId = pathSegments[pathSegments.length - 1];

    if (!orderId || orderId === 'orders') {
        container.innerHTML = `
            <div class="card" style="padding: 2.5rem; text-align: center; color: #f87171;">
                <p>Không tìm thấy mã đơn hàng hợp lệ.</p>
                <a href="${API_BASE}/orders" class="btn-primary" style="display: inline-block; width: auto; margin-top: 1rem; text-decoration: none;">Quay lại danh sách đơn</a>
            </div>
        `;
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/api/orders/${encodeURIComponent(orderId)}`, {
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        });

        if (res.status === 401) {
            container.innerHTML = `
                <div class="card" style="padding: 2.5rem; text-align: center;">
                    <p style="color: var(--text-muted); margin-bottom: 1rem;">Vui lòng đăng nhập để xem chi tiết đơn hàng này.</p>
                    <a href="${API_BASE}/auth/login?returnTo=${encodeURIComponent(window.location.pathname)}" class="btn-primary" style="display: inline-block; width: auto; margin: 0 auto; text-decoration: none;">Đăng nhập</a>
                </div>
            `;
            return;
        }

        if (res.status === 403) {
            container.innerHTML = `
                <div class="card" style="padding: 2.5rem; text-align: center;">
                    <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">🔒</div>
                    <h3 style="color: #f87171; margin-bottom: 0.5rem;">Không có quyền truy cập</h3>
                    <p style="color: var(--text-muted); margin-bottom: 1.5rem;">Đơn hàng này không thuộc tài khoản của bạn.</p>
                    <a href="${API_BASE}/orders" class="btn-primary" style="display: inline-block; width: auto; margin: 0 auto; text-decoration: none;">Quay lại đơn hàng của tôi</a>
                </div>
            `;
            return;
        }

        const data = await res.json();

        if (res.ok && data.success && data.data) {
            const o = data.data;
            container.innerHTML = '';
            
            // 1. Header
            const header = document.createElement('div');
            header.className = 'tracking-header card';
            header.style.padding = '1.75rem';
            header.style.marginBottom = '1.5rem';
            
            const titleRow = document.createElement('div');
            titleRow.style.display = 'flex';
            titleRow.style.justifyContent = 'space-between';
            titleRow.style.alignItems = 'center';
            titleRow.style.flexWrap = 'wrap';
            titleRow.style.gap = '1rem';
            
            const h1 = document.createElement('h1');
            h1.textContent = 'Đơn hàng #' + o.orderId;
            
            const badge = document.createElement('span');
            badge.className = 'order-status-badge ' + getBadgeClass(o.status);
            badge.style.fontSize = '0.9rem';
            badge.style.padding = '0.4rem 1rem';
            badge.textContent = formatStatus(o.status);
            
            titleRow.append(h1, badge);
            
            const pDate = document.createElement('p');
            pDate.textContent = 'Thời gian đặt: ' + new Date(o.orderedAt).toLocaleString('vi-VN');
            
            header.append(titleRow, pDate);
            container.appendChild(header);
            
            // 2. Order Stepper / Timeline
            const stepperCard = document.createElement('div');
            stepperCard.className = 'card';
            stepperCard.style.padding = '1.5rem';
            stepperCard.style.marginBottom = '1.5rem';
            
            if (o.status === 'CANCELLED') {
                stepperCard.innerHTML = `
                    <div style="background: rgba(239, 68, 68, 0.1); border: 1px solid #f87171; padding: 1.25rem; border-radius: 10px; text-align: center; color: #f87171;">
                        <span style="font-size: 1.5rem; display: block; margin-bottom: 0.25rem;">✕</span>
                        <strong>Đơn hàng đã bị hủy</strong>
                        <p style="margin: 0.25rem 0 0; font-size: 0.85rem; color: var(--text-muted);">Đơn hàng đã được ghi nhận hủy trong hệ thống.</p>
                    </div>
                `;
            } else {
                stepperCard.appendChild(buildStepper(o.status, o.fulfillmentType));
            }
            container.appendChild(stepperCard);
            
            // 3. Info Grid (Người nhận & Thanh toán)
            const grid = document.createElement('div');
            grid.style.display = 'grid';
            grid.style.gridTemplateColumns = 'repeat(auto-fit, minmax(min(280px, 100%), 1fr))';
            grid.style.gap = '1.5rem';
            grid.style.marginBottom = '1.5rem';
            
            const col1 = document.createElement('div');
            col1.className = 'card';
            col1.style.padding = '1.5rem';
            col1.innerHTML = `
                <h3 style="margin-top: 0; margin-bottom: 1rem; font-size: 1.1rem; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;">📍 Thông tin giao nhận</h3>
                <p style="margin: 0.4rem 0;"><strong>Người nhận:</strong> ${escapeHtml(o.receiverName)}</p>
                <p style="margin: 0.4rem 0;"><strong>Số điện thoại:</strong> ${escapeHtml(o.receiverPhone)}</p>
                <p style="margin: 0.4rem 0;"><strong>Hình thức:</strong> ${o.fulfillmentType === 'DELIVERY' ? 'Giao hàng tận nơi' : 'Lấy tại quán'}</p>
                <p style="margin: 0.4rem 0;"><strong>Địa chỉ:</strong> ${escapeHtml(o.deliveryAddress || 'Nhận tại quầy nhà hàng')}</p>
                <p style="margin: 0.4rem 0; color: var(--text-muted); font-size: 0.9rem;"><strong>Ghi chú:</strong> ${escapeHtml(o.customerNote || 'Không có ghi chú')}</p>
            `;
            
            const col2 = document.createElement('div');
            col2.className = 'card';
            col2.style.padding = '1.5rem';
            const payStatus = o.payment?.status ? formatPaymentStatus(o.payment.status) : 'Chưa có thông tin';
            const payMethod = o.payment?.method ? formatPaymentMethod(o.payment.method) : 'Tiền mặt';
            col2.innerHTML = `
                <h3 style="margin-top: 0; margin-bottom: 1rem; font-size: 1.1rem; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;">💳 Thông tin thanh toán</h3>
                <p style="margin: 0.4rem 0;"><strong>Phương thức:</strong> ${payMethod}</p>
                <p style="margin: 0.4rem 0;"><strong>Trạng thái:</strong> <span style="font-weight: 700;">${payStatus}</span></p>
                ${o.payment?.paymentId ? `<p style="margin: 0.4rem 0; font-size: 0.85rem; color: var(--text-muted);">Mã thanh toán: #${escapeHtml(o.payment.paymentId)}</p>` : ''}
            `;
            
            grid.append(col1, col2);
            container.appendChild(grid);
            
            // 4. Danh sách món ăn
            const itemCard = document.createElement('div');
            itemCard.className = 'card';
            itemCard.style.padding = '1.5rem';
            itemCard.style.marginBottom = '1.5rem';
            
            const itemTitle = document.createElement('h3');
            itemTitle.style.marginTop = '0';
            itemTitle.style.marginBottom = '1rem';
            itemTitle.style.fontSize = '1.1rem';
            itemTitle.style.borderBottom = '1px solid var(--border)';
            itemTitle.style.paddingBottom = '0.5rem';
            itemTitle.textContent = '🍲 Danh sách món đã đặt';
            itemCard.appendChild(itemTitle);
            
            if (o.items && o.items.length > 0) {
                o.items.forEach(item => {
                    const row = document.createElement('div');
                    row.style.display = 'flex';
                    row.style.justifyContent = 'space-between';
                    row.style.alignItems = 'flex-start';
                    row.style.padding = '0.75rem 0';
                    row.style.borderBottom = '1px dashed var(--border)';
                    
                    const left = document.createElement('div');
                    left.innerHTML = `
                        <div style="font-weight: 700; font-size: 1rem;">${item.quantity}x ${escapeHtml(item.foodName || item.foodNameSnapshot)}</div>
                        ${item.options && item.options.length > 0 ? `
                            <div style="font-size: 0.85rem; color: var(--text-muted); margin-top: 0.2rem;">
                                ${item.options.map(opt => `+ ${escapeHtml(opt.optionName)} (${Number(opt.extraPrice).toLocaleString('vi-VN')} đ)`).join(', ')}
                            </div>
                        ` : ''}
                        ${item.note ? `<div style="font-size: 0.85rem; color: var(--accent); font-style: italic; margin-top: 0.2rem;">Ghi chú: ${escapeHtml(item.note)}</div>` : ''}
                    `;
                    
                    const right = document.createElement('div');
                    right.style.fontWeight = '700';
                    right.style.fontSize = '1rem';
                    const lineTotal = Number(item.lineTotal) || (Number(item.unitPrice) * Number(item.quantity)) || 0;
                    right.textContent = lineTotal.toLocaleString('vi-VN') + ' đ';
                    
                    row.append(left, right);
                    itemCard.appendChild(row);
                });
            } else {
                itemCard.innerHTML += '<p style="color: var(--text-muted);">Không có chi tiết món ăn.</p>';
            }
            container.appendChild(itemCard);
            
            // 5. Tổng kết chi phí
            const summaryCard = document.createElement('div');
            summaryCard.className = 'card';
            summaryCard.style.padding = '1.5rem';
            
            const subtotal = Number(o.subtotal) || 0;
            const deliveryFee = Number(o.deliveryFee) || 0;
            const discount = Number(o.discountAmount) || 0;
            const total = Number(o.totalAmount) || (subtotal + deliveryFee - discount);
            
            summaryCard.innerHTML = `
                <h3 style="margin-top: 0; margin-bottom: 1rem; font-size: 1.1rem; border-bottom: 1px solid var(--border); padding-bottom: 0.5rem;">🧾 Tổng kết thanh toán</h3>
                <div style="display: flex; justify-content: space-between; padding: 0.35rem 0;">
                    <span style="color: var(--text-muted);">Tạm tính món ăn:</span>
                    <span>${subtotal.toLocaleString('vi-VN')} đ</span>
                </div>
                <div style="display: flex; justify-content: space-between; padding: 0.35rem 0;">
                    <span style="color: var(--text-muted);">Phí vận chuyển:</span>
                    <span>${deliveryFee.toLocaleString('vi-VN')} đ</span>
                </div>
                ${discount > 0 ? `
                    <div style="display: flex; justify-content: space-between; padding: 0.35rem 0; color: #4ade80;">
                        <span>Mã khuyến mãi:</span>
                        <span>-${discount.toLocaleString('vi-VN')} đ</span>
                    </div>
                ` : ''}
                <div style="display: flex; justify-content: space-between; padding-top: 1rem; margin-top: 0.5rem; border-top: 1px solid var(--border); font-size: 1.25rem; font-weight: 800; color: var(--accent);">
                    <span>Tổng cộng:</span>
                    <span>${total.toLocaleString('vi-VN')} đ</span>
                </div>
            `;
            container.appendChild(summaryCard);
            
        } else {
            container.innerHTML = `
                <div class="card" style="padding: 2rem; text-align: center; color: #f87171;">
                    <p>${escapeHtml(data?.error?.message || data?.message || 'Không tìm thấy thông tin đơn hàng.')}</p>
                    <a href="${API_BASE}/orders" class="btn-primary" style="display: inline-block; width: auto; margin-top: 1rem; text-decoration: none;">Quay lại danh sách đơn</a>
                </div>
            `;
        }
    } catch (err) {
        container.innerHTML = `
            <div class="card" style="padding: 2rem; text-align: center; color: #f87171;">
                <p>Lỗi kết nối máy chủ. Vui lòng thử lại sau.</p>
                <a href="${API_BASE}/orders" class="btn-primary" style="display: inline-block; width: auto; margin-top: 1rem; text-decoration: none;">Quay lại danh sách</a>
            </div>
        `;
    }
});

function buildStepper(currentStatus, fulfillmentType) {
    const steps = [
        { key: 'PENDING_CONFIRMATION', num: '1', label: 'Chờ xác nhận' },
        { key: 'PREPARING', num: '2', label: 'Đang chuẩn bị' },
        { key: 'DELIVERING', num: '3', label: 'Đang giao hàng' },
        { key: 'COMPLETED', num: '4', label: 'Hoàn tất' }
    ].filter(step => fulfillmentType !== 'PICKUP' || step.key !== 'DELIVERING');

    const curIdx = steps.findIndex(step => step.key === currentStatus);

    const stepperDiv = document.createElement('div');
    stepperDiv.className = 'stepper';

    steps.forEach((s, idx) => {
        const stepEl = document.createElement('div');
        stepEl.className = 'step';
        if (idx < curIdx) stepEl.classList.add('done');
        else if (idx === curIdx) stepEl.classList.add('active');

        const iconEl = document.createElement('div');
        iconEl.className = 'step-icon';
        iconEl.textContent = idx < curIdx ? '✓' : String(idx + 1);

        const labelEl = document.createElement('div');
        labelEl.className = 'step-label';
        labelEl.textContent = s.label;

        stepEl.append(iconEl, labelEl);
        stepperDiv.appendChild(stepEl);
    });

    return stepperDiv;
}

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

function formatPaymentStatus(status) {
    const map = {
        'PENDING': 'Chờ thanh toán',
        'SUCCESS': 'Đã thanh toán',
        'FAILED': 'Thanh toán thất bại',
        'REFUNDED': 'Đã hoàn tiền'
    };
    return map[status] || status;
}

function formatPaymentMethod(method) {
    const map = {
        'CASH': 'Tiền mặt khi nhận hàng (COD)',
        'BANK_TRANSFER': 'Chuyển khoản ngân hàng',
        'E_WALLET': 'Ví điện tử'
    };
    return map[method] || method;
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/[&<>'"]/g, tag => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    }[tag] || tag));
}
