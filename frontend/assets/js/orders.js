document.addEventListener('DOMContentLoaded', async () => {
    loadOrders();
});

async function loadOrders() {
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
                priceDiv.textContent = (Number(o.totalAmount) || 0).toLocaleString('vi-VN') + '\u00A0đ';
                
                const badge = document.createElement('span');
                badge.className = 'order-status-badge ' + getBadgeClass(o.status);
                badge.textContent = formatStatus(o.status);
                
                const actionDiv = document.createElement('div');
                actionDiv.style.marginTop = '0.5rem';
                actionDiv.style.display = 'flex';
                actionDiv.style.alignItems = 'center';
                actionDiv.style.justifyContent = 'flex-end';
                actionDiv.style.gap = '0.75rem';

                if (o.status === 'PENDING_CONFIRMATION' || o.status === 'PREPARING') {
                    const btnCancel = document.createElement('button');
                    btnCancel.type = 'button';
                    btnCancel.className = 'btn-cancel-order';
                    btnCancel.textContent = '✕ Hủy đơn';
                    btnCancel.title = 'Hủy đơn hàng này';
                    btnCancel.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        showCancelModal(o.orderId, API_BASE, () => loadOrders());
                    });
                    actionDiv.appendChild(btnCancel);
                }

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
                    <p>${escapeHtml(data?.error?.message || data?.message || 'Không thể tải danh sách đơn hàng.')}</p>
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

function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, character => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[character]));
}

function showCancelModal(orderId, apiBase, onCancelled) {
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';

    const reasons = [
        'Đổi ý, không muốn đặt nữa',
        'Đặt nhầm món / nhầm địa chỉ',
        'Thời gian giao hàng quá lâu',
        'Muốn thay đổi phương thức thanh toán',
        'Lý do khác'
    ];

    backdrop.innerHTML = `
        <div class="modal-dialog" role="dialog" aria-modal="true" aria-labelledby="cancelModalTitle" style="max-width: 480px; width: 95%;">
            <div class="modal-header">
                <h3 id="cancelModalTitle">✕ Xác nhận hủy đơn hàng</h3>
                <button type="button" class="modal-close" aria-label="Đóng">&times;</button>
            </div>
            <div class="modal-body" style="gap: 1rem;">
                <p style="margin: 0; color: var(--text-muted); font-size: 0.95rem; line-height: 1.5;">
                    Bạn có chắc chắn muốn hủy đơn hàng <strong>#${escapeHtml(orderId)}</strong> không? Quán rất tiếc vì trải nghiệm chưa trọn vẹn này.
                </p>
                <div style="display: flex; flex-direction: column; gap: 0.5rem; margin-top: 0.25rem;">
                    <span style="font-weight: 700; font-size: 0.85rem; color: var(--text);">Vui lòng chọn lý do hủy:</span>
                    ${reasons.map((r, i) => `
                        <label class="cancel-reason-option">
                            <input type="radio" name="cancelReason" value="${escapeHtml(r)}" ${i === 0 ? 'checked' : ''}>
                            <span>${escapeHtml(r)}</span>
                        </label>
                    `).join('')}
                    <div id="otherReasonWrap" style="display: none; margin-top: 0.25rem;">
                        <input type="text" id="otherReasonInput" placeholder="Nhập lý do cụ thể..." maxlength="200" style="padding: 0.6rem 0.8rem; font-size: 0.9rem;" />
                    </div>
                </div>
                <div id="cancelErrorMsg" style="display: none; color: #f87171; font-size: 0.85rem;"></div>
            </div>
            <div class="modal-footer" style="display: flex; justify-content: flex-end; gap: 0.75rem;">
                <button type="button" class="btn-cancel-modal-close" style="padding: 0.6rem 1.2rem; border-radius: 8px; border: 1px solid var(--border); background: var(--bg-card-alt); color: var(--text); font-weight: 600; cursor: pointer;">Không, giữ đơn</button>
                <button type="button" id="btnConfirmCancel" style="padding: 0.6rem 1.25rem; border-radius: 8px; border: 0; background: #ef4444; color: #fff; font-weight: 700; cursor: pointer; transition: background 0.15s ease;">Xác nhận hủy đơn</button>
            </div>
        </div>
    `;

    document.body.appendChild(backdrop);

    const closeModal = () => backdrop.remove();

    backdrop.querySelector('.modal-close').addEventListener('click', closeModal);
    backdrop.querySelector('.btn-cancel-modal-close').addEventListener('click', closeModal);
    backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) closeModal();
    });

    const radioList = backdrop.querySelectorAll('input[name="cancelReason"]');
    const otherWrap = backdrop.querySelector('#otherReasonWrap');
    const otherInput = backdrop.querySelector('#otherReasonInput');

    radioList.forEach(radio => {
        radio.addEventListener('change', () => {
            if (radio.value === 'Lý do khác' && radio.checked) {
                otherWrap.style.display = 'block';
                otherInput.focus();
            } else {
                otherWrap.style.display = 'none';
            }
        });
    });

    const confirmBtn = backdrop.querySelector('#btnConfirmCancel');
    const errorMsg = backdrop.querySelector('#cancelErrorMsg');

    confirmBtn.addEventListener('click', async () => {
        let reason = backdrop.querySelector('input[name="cancelReason"]:checked')?.value || '';
        if (reason === 'Lý do khác') {
            const custom = otherInput.value.trim();
            reason = custom ? custom : 'Lý do khác';
        }

        confirmBtn.disabled = true;
        confirmBtn.textContent = 'Đang xử lý...';
        confirmBtn.style.opacity = '0.7';
        errorMsg.style.display = 'none';

        try {
            const res = await fetch(`${apiBase}/api/orders/${encodeURIComponent(orderId)}/cancel`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({ reason })
            });

            const data = await res.json();
            if (res.ok && data.success) {
                closeModal();
                showToast('Đơn hàng #' + orderId + ' đã được hủy thành công.', 'success');
                if (typeof onCancelled === 'function') {
                    onCancelled();
                }
            } else {
                const msg = data.error?.message || data.message || 'Không thể hủy đơn hàng này.';
                errorMsg.textContent = msg;
                errorMsg.style.display = 'block';
                confirmBtn.disabled = false;
                confirmBtn.textContent = 'Xác nhận hủy đơn';
                confirmBtn.style.opacity = '1';
            }
        } catch (err) {
            errorMsg.textContent = 'Lỗi kết nối máy chủ: ' + err.message;
            errorMsg.style.display = 'block';
            confirmBtn.disabled = false;
            confirmBtn.textContent = 'Xác nhận hủy đơn';
            confirmBtn.style.opacity = '1';
        }
    });
}

function showToast(message, type = 'info') {
    let container = document.querySelector('.crave-toast-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'crave-toast-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `crave-toast ${type}`;
    const icon = type === 'success' ? '✓ ' : (type === 'error' ? '⚠️ ' : 'ℹ️ ');
    toast.innerHTML = `<span><strong>${icon}</strong>${escapeHtml(message)}</span>`;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        toast.style.transition = 'all 0.25s ease';
        setTimeout(() => toast.remove(), 250);
    }, 3200);
}

