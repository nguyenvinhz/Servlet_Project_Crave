document.addEventListener('DOMContentLoaded', () => {
    const context = document.body.dataset.contextPath || '';
    document.querySelectorAll('[data-date]').forEach(time => {
        const date = new Date(time.dataset.date);
        if (!Number.isNaN(date.getTime())) time.textContent = date.toLocaleString('vi-VN');
    });
    document.querySelectorAll('[data-cancel-order]').forEach(button => {
        button.addEventListener('click', () => showCancelModal(button.dataset.cancelOrder, context));
    });
});

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
                <div id="cancelErrorMsg" style="display: none; color: var(--error, #b42318); font-size: 0.85rem;"></div>
            </div>
            <div class="modal-footer" style="display: flex; justify-content: flex-end; gap: 0.75rem;">
                <button type="button" class="btn-cancel-modal-close" style="padding: 0.6rem 1.2rem; border-radius: 8px; border: 1px solid var(--border); background: var(--bg-card-alt); color: var(--text); font-weight: 600; cursor: pointer;">Không, giữ đơn</button>
                <button type="button" id="btnConfirmCancel" style="padding: 0.6rem 1.25rem; border-radius: 8px; border: 0; background: var(--error, #b42318); color: #fff; font-weight: 700; cursor: pointer; transition: background 0.15s ease;">Xác nhận hủy đơn</button>
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

    confirmBtn.addEventListener('click', () => {
        let reason = backdrop.querySelector('input[name="cancelReason"]:checked')?.value || '';
        if (reason === 'Lý do khác') reason = otherInput.value.trim() || reason;
        const form = document.createElement('form');
        form.method = 'post'; form.action = apiBase + '/orders/cancel';
        for (const [name, value] of Object.entries({orderId, reason})) {
            const input = document.createElement('input');
            input.type = 'hidden'; input.name = name; input.value = value; form.appendChild(input);
        }
        confirmBtn.disabled = true; confirmBtn.textContent = 'Đang xử lý...';
        document.body.appendChild(form); form.submit();
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

