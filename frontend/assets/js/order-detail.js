document.addEventListener('DOMContentLoaded', async () => {
    loadOrderDetail();
});

let activePayosPayment = null;

window.addEventListener('pageshow', event => {
    if (event.persisted) loadOrderDetail();
});

async function loadOrderDetail() {
    activePayosPayment?.dispose();
    activePayosPayment = null;
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
            
            const rightHeader = document.createElement('div');
            rightHeader.style.display = 'flex';
            rightHeader.style.alignItems = 'center';
            rightHeader.style.gap = '0.75rem';
            rightHeader.style.flexWrap = 'wrap';

            const badge = document.createElement('span');
            badge.className = 'order-status-badge ' + getBadgeClass(o.status);
            badge.style.fontSize = '0.9rem';
            badge.style.padding = '0.4rem 1rem';
            badge.textContent = formatStatus(o.status);
            rightHeader.appendChild(badge);

            if (o.status === 'PENDING_CONFIRMATION' || o.status === 'PREPARING') {
                const btnCancel = document.createElement('button');
                btnCancel.type = 'button';
                btnCancel.className = 'btn-cancel-order';
                btnCancel.innerHTML = '✕ Hủy đơn';
                btnCancel.title = 'Hủy đơn hàng này';
                btnCancel.addEventListener('click', () => {
                    showCancelModal(o.orderId, API_BASE, () => loadOrderDetail());
                });
                rightHeader.appendChild(btnCancel);
            }
            
            titleRow.append(h1, rightHeader);
            
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
                const cancelHistory = o.history && o.history.length > 0 
                    ? o.history.slice().reverse().find(h => h.status === 'CANCELLED') 
                    : null;
                const cancelNote = cancelHistory?.note ? `<p style="margin: 0.5rem 0 0; font-size: 0.9rem; color: #fca5a5; font-style: italic;">Lý do: ${escapeHtml(cancelHistory.note)}</p>` : '';
                stepperCard.innerHTML = `
                    <div style="background: rgba(239, 68, 68, 0.1); border: 1px solid #f87171; padding: 1.25rem; border-radius: 10px; text-align: center; color: #f87171;">
                        <span style="font-size: 1.5rem; display: block; margin-bottom: 0.25rem;">✕</span>
                        <strong>Đơn hàng đã bị hủy</strong>
                        <p style="margin: 0.25rem 0 0; font-size: 0.85rem; color: var(--text-muted);">Đơn hàng đã được ghi nhận hủy trong hệ thống.</p>
                        ${cancelNote}
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
                <p style="margin: 0.4rem 0;"><strong>Trạng thái:</strong> <span data-payment-status style="font-weight: 700;">${payStatus}</span></p>
                ${o.payment?.paymentId ? `<p style="margin: 0.4rem 0; font-size: 0.85rem; color: var(--text-muted);">Mã thanh toán: #${escapeHtml(o.payment.paymentId)}</p>` : ''}
            `;
            
            grid.append(col1, col2);
            container.appendChild(grid);

            if (['BANK_TRANSFER', 'E_WALLET'].includes(o.payment?.method)
                    && !['SUCCESS', 'REFUNDED'].includes(o.payment?.status) && o.status !== 'CANCELLED') {
                activePayosPayment = showPayosPayment(o, API_BASE, container,
                    col2.querySelector('[data-payment-status]'));
            }
            
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
                                ${item.options.map(opt => `+ ${escapeHtml(opt.optionName)} (${Number(opt.extraPrice).toLocaleString('vi-VN')}\u00A0đ)`).join(', ')}
                            </div>
                        ` : ''}
                        ${item.note ? `<div style="font-size: 0.85rem; color: var(--accent); font-style: italic; margin-top: 0.2rem;">Ghi chú: ${escapeHtml(item.note)}</div>` : ''}
                    `;
                    
                    const right = document.createElement('div');
                    right.style.fontWeight = '700';
                    right.style.fontSize = '1rem';
                    const lineTotal = Number(item.lineTotal) || (Number(item.unitPrice) * Number(item.quantity)) || 0;
                    right.textContent = lineTotal.toLocaleString('vi-VN') + '\u00A0đ';
                    
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
                    <span>${subtotal.toLocaleString('vi-VN')}\u00A0đ</span>
                </div>
                <div style="display: flex; justify-content: space-between; padding: 0.35rem 0;">
                    <span style="color: var(--text-muted);">Phí vận chuyển:</span>
                    <span>${deliveryFee.toLocaleString('vi-VN')}\u00A0đ</span>
                </div>
                ${discount > 0 ? `
                    <div style="display: flex; justify-content: space-between; padding: 0.35rem 0; color: #4ade80;">
                        <span>Mã khuyến mãi:</span>
                        <span>-${discount.toLocaleString('vi-VN')}\u00A0đ</span>
                    </div>
                ` : ''}
                <div style="display: flex; justify-content: space-between; padding-top: 1rem; margin-top: 0.5rem; border-top: 1px solid var(--border); font-size: 1.25rem; font-weight: 800; color: var(--accent);">
                    <span>Tổng cộng:</span>
                    <span>${total.toLocaleString('vi-VN')}\u00A0đ</span>
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
}

function showPayosPayment(order, apiBase, container, paymentStatusElement) {
    const template = document.getElementById('payosPaymentTemplate');
    if (!template) return null;
    const content = template.content.cloneNode(true);
    const card = content.querySelector('#payosPayment');
    const status = content.querySelector('#payosStatus');
    const details = content.querySelector('#payosDetails');
    const qr = content.querySelector('#payosQr');
    const qrError = content.querySelector('#payosQrError');
    const checkoutLink = content.querySelector('#payosCheckoutLink');
    const retry = content.querySelector('#payosRetry');
    const actions = content.querySelector('#payosActions');
    const hint = content.querySelector('#payosHint');
    const amount = content.querySelector('#payosAmount');
    const accountNumber = content.querySelector('#payosAccountNumber');
    const accountName = content.querySelector('#payosAccountName');
    const description = content.querySelector('#payosDescription');
    const endpoint = `${apiBase}/api/payos/orders/${encodeURIComponent(order.orderId)}`;
    container.appendChild(content);

    let disposed = false;
    let busy = false;
    let terminal = false;
    let hasLink = false;
    let hasError = false;
    let retryMethod = 'POST';
    let timer = null;
    let controller = null;
    let resumeWhenVisible = false;
    let qrVersion = null;

    function clearPoll() {
        if (timer !== null) window.clearTimeout(timer);
        timer = null;
    }

    function schedulePoll() {
        clearPoll();
        if (!disposed && !document.hidden && !terminal && hasLink && !hasError && !busy) {
            timer = window.setTimeout(() => requestPayment('GET'), 5000);
        }
    }

    function safeCheckoutUrl(value) {
        try {
            const url = new URL(value);
            return url.protocol === 'https:' && url.hostname === 'pay.payos.vn'
                && (!url.port || url.port === '443') && !url.username && !url.password ? url.href : null;
        } catch (_) {
            return null;
        }
    }

    function render(data) {
        if (!['PENDING', 'PROCESSING', 'PAID', 'REFUNDED', 'CANCELLED', 'EXPIRED'].includes(data.status)
                || !['PENDING', 'SUCCESS', 'FAILED', 'REFUNDED'].includes(data.paymentStatus)
                || String(data.orderId) !== String(order.orderId)) {
            throw new Error('Thông tin thanh toán chưa hợp lệ. Vui lòng thử lại.');
        }
        const paid = data.paymentStatus === 'SUCCESS' || data.status === 'PAID';
        const refunded = data.paymentStatus === 'REFUNDED' || data.status === 'REFUNDED';
        terminal = paid || refunded || data.status === 'CANCELLED' || data.status === 'EXPIRED';
        hasLink = true;
        hasError = false;
        qrError.hidden = true;
        card.classList.toggle('payos-paid', paid && !refunded);
        paymentStatusElement.textContent = formatPaymentStatus(refunded ? 'REFUNDED' : paid ? 'SUCCESS' : data.paymentStatus);
        details.hidden = terminal;
        hint.hidden = terminal;
        checkoutLink.hidden = true;
        checkoutLink.removeAttribute('href');
        qr.hidden = true;
        retry.hidden = terminal;
        actions.hidden = terminal;

        if (terminal) {
            clearPoll();
            qr.removeAttribute('src');
            status.textContent = refunded ? 'Đơn hàng đã được hoàn tiền.' : paid
                ? 'Đã nhận thanh toán. Cảm ơn bạn!'
                : data.status === 'EXPIRED' ? 'Mã thanh toán đã hết hạn. Vui lòng liên hệ nhà hàng hoặc đặt đơn mới để thanh toán.'
                    : 'Giao dịch đã bị hủy. Vui lòng liên hệ nhà hàng hoặc đặt đơn mới để thanh toán.';
            return;
        }

        const serverAmount = Number(data.amount);
        if (!Number.isFinite(serverAmount) || serverAmount <= 0 || data.currency !== 'VND') {
            throw new Error('Chưa nhận được số tiền thanh toán hợp lệ. Vui lòng thử lại.');
        }
        amount.textContent = new Intl.NumberFormat('vi-VN').format(serverAmount) + ' đ';
        accountNumber.textContent = data.accountNumber || 'Xem trên trang thanh toán';
        accountName.textContent = data.accountName || 'Xem trên trang thanh toán';
        description.textContent = data.description || 'Xem trên trang thanh toán';
        status.textContent = 'Chờ thanh toán. Trạng thái sẽ tự cập nhật khi hệ thống nhận được tiền.';
        retryMethod = 'GET';
        retry.textContent = 'Kiểm tra thanh toán';
        const url = safeCheckoutUrl(data.checkoutUrl);
        if (url) {
            checkoutLink.href = url;
            checkoutLink.hidden = false;
        }
        if (data.qrCode) {
            const version = String(data.orderCode || data.paymentId || 'current');
            if (qrVersion !== version || !qr.hasAttribute('src')) {
                qrVersion = version;
                qr.src = `${endpoint}/qr?v=${encodeURIComponent(version)}`;
            }
            qr.hidden = false;
        } else {
            qrError.textContent = 'Chưa tải được mã QR. Bạn có thể mở trang thanh toán hoặc thử kiểm tra lại.';
            qrError.hidden = false;
        }
    }

    async function requestPayment(method) {
        if (disposed || busy || document.hidden) return;
        clearPoll();
        busy = true;
        controller = new AbortController();
        const requestController = controller;
        card.setAttribute('aria-busy', 'true');
        retry.disabled = true;
        if (method === 'POST') status.textContent = 'Đang tạo mã thanh toán...';
        try {
            const response = await fetch(endpoint, {
                method, credentials: 'same-origin', signal: requestController.signal,
                headers: { 'Accept': 'application/json' }
            });
            if (requestController.signal.aborted || disposed) return;
            if (response.status === 401) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để thanh toán.');
            const envelope = await response.json();
            if (requestController.signal.aborted || disposed) return;
            if (!response.ok || !envelope.success || !envelope.data) {
                throw new Error(envelope.error?.message || 'Không thể tải thông tin thanh toán. Vui lòng thử lại.');
            }
            render(envelope.data);
        } catch (error) {
            if (requestController.signal.aborted || disposed) return;
            hasError = true;
            details.hidden = true;
            checkoutLink.hidden = true;
            checkoutLink.removeAttribute('href');
            qr.hidden = true;
            qr.removeAttribute('src');
            status.textContent = error.message || 'Lỗi kết nối máy chủ. Vui lòng thử lại.';
            retryMethod = hasLink && !terminal ? 'GET' : 'POST';
            retry.hidden = false;
            retry.textContent = 'Thử lại';
            actions.hidden = false;
        } finally {
            busy = false;
            controller = null;
            if (!disposed) {
                card.setAttribute('aria-busy', 'false');
                retry.disabled = false;
                if (resumeWhenVisible && !document.hidden && !terminal) {
                    resumeWhenVisible = false;
                    requestPayment(hasLink ? 'GET' : 'POST');
                } else {
                    schedulePoll();
                }
            }
        }
    }

    function onVisibilityChange() {
        if (document.hidden) {
            clearPoll();
            controller?.abort();
        } else if (!terminal) {
            if (busy) resumeWhenVisible = true;
            else requestPayment(hasLink ? 'GET' : 'POST');
        }
    }

    function dispose() {
        disposed = true;
        clearPoll();
        controller?.abort();
        document.removeEventListener('visibilitychange', onVisibilityChange);
        window.removeEventListener('pagehide', dispose);
    }

    qr.addEventListener('error', () => {
        if (disposed || terminal) return;
        qr.hidden = true;
        qrError.textContent = 'Không tải được mã QR. Bạn có thể mở trang thanh toán hoặc kiểm tra lại.';
        qrError.hidden = false;
        qr.removeAttribute('src');
    });
    retry.addEventListener('click', () => requestPayment(retryMethod));
    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('pagehide', dispose);
    requestPayment('POST');
    return { dispose };
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
