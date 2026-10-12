(function () {
    'use strict';

    const API_BASE = document.body.dataset.contextPath || '';
    const VOUCHER_KEY = 'crave_applied_voucher';
    const state = {
        subtotal: 0, discount: 0, promotionId: null,
        ready: false, loading: false, submitting: false, completed: false, sessionExpired: false
    };
    let elements;

    function clearVoucher() {
        state.promotionId = null;
        state.discount = 0;
        try { sessionStorage.removeItem(VOUCHER_KEY); } catch (_) { }
        const url = new URL(window.location.href);
        if (url.searchParams.has('voucher') && !state.completed && !state.sessionExpired) {
            url.searchParams.delete('voucher');
            window.history.replaceState(null, '', url);
        }
    }

    function voucherCode() {
        const fromUrl = new URLSearchParams(window.location.search).get('voucher');
        if (fromUrl) return fromUrl.trim().toUpperCase();
        try {
            const saved = JSON.parse(sessionStorage.getItem(VOUCHER_KEY));
            return typeof saved?.code === 'string' ? saved.code.trim().toUpperCase() : '';
        } catch (_) {
            return '';
        }
    }

    function alert(message, success = false) {
        elements.alert.textContent = message;
        elements.alert.className = `alert ${success ? 'success' : 'error'}`;
        elements.alert.style.display = 'block';
    }

    function updateControls() {
        elements.submit.disabled = !state.ready || state.loading || state.submitting || state.completed || state.sessionExpired;
        elements.retry.hidden = state.ready || state.loading || state.sessionExpired || state.completed;
        elements.form.setAttribute('aria-busy', String(state.loading || state.submitting));
    }

    function updateSummary() {
        const fee = state.subtotal > 0 && elements.fulfillment.value === 'DELIVERY' ? 15000 : 0;
        const price = amount => new Intl.NumberFormat('vi-VN').format(amount) + ' đ';
        elements.subtotal.textContent = price(state.subtotal);
        elements.fee.textContent = price(fee);
        elements.discount.textContent = '-' + price(state.discount);
        elements.discountRow.hidden = state.discount <= 0;
        elements.total.textContent = price(Math.max(0, state.subtotal + fee - state.discount));
    }

    async function requestApi(path, options = {}) {
        const response = await fetch(`${API_BASE}${path}`, {
            ...options,
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json', ...options.headers }
        });
        if (response.status === 401) {
            state.sessionExpired = true;
            state.ready = false;
            state.subtotal = 0;
            clearVoucher();
            updateSummary();
            elements.form.reset();
            updateControls();
            alert('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
            const query = new URLSearchParams({
                reason: 'session-expired', returnTo: `${window.location.pathname}${window.location.search}`
            });
            window.location.assign(`${API_BASE}/auth/login?${query}`);
            return null;
        }
        const data = await response.json();
        if (!response.ok) data.success = false;
        return data;
    }

    function message(data, fallback) {
        return data?.error?.message || data?.message || fallback;
    }

    async function loadCheckout(warning = '', blockOrder = false) {
        if (state.loading || state.submitting || state.completed || state.sessionExpired) return;
        state.loading = true;
        state.ready = false;
        elements.alert.style.display = 'none';
        updateControls();
        try {
            const data = await requestApi('/api/cart');
            if (!data) return;
            if (!data.success || !data.data) throw new Error(message(data, 'Không thể tải giỏ hàng.'));
            state.subtotal = Number(data.data.subtotal) || 0;
            state.discount = 0;
            state.promotionId = null;
            if (!data.data.items?.length || state.subtotal <= 0) {
                clearVoucher();
                updateSummary();
                throw new Error('Giỏ hàng đang trống. Vui lòng thêm món trước khi đặt hàng.');
            }

            const code = voucherCode();
            if (code) {
                const validation = await requestApi('/api/promotions/validate', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ code, subtotal: state.subtotal })
                });
                if (!validation) return;
                if (validation.success && validation.data?.valid && validation.data.promotion?.promotionId) {
                    // Use the freshly validated server amount, never the amount saved by the cart page.
                    state.subtotal = Number(validation.data.originalSubtotal ?? state.subtotal);
                    state.discount = Number(validation.data.discountAmount) || 0;
                    state.promotionId = validation.data.promotion.promotionId;
                } else if (validation.error?.code?.startsWith('PROMOTION_') || validation.data?.valid === false) {
                    clearVoucher();
                    alert(message(validation, validation.data?.message || 'Mã khuyến mãi không còn hợp lệ. Vui lòng kiểm tra tổng tiền trước khi đặt hàng.'));
                } else {
                    throw new Error(message(validation, 'Không thể xác thực voucher. Vui lòng thử lại.'));
                }
            }
            state.ready = !blockOrder;
            updateSummary();
            if (warning) alert(warning);
        } catch (error) {
            updateSummary();
            alert(error.message || 'Lỗi kết nối máy chủ. Vui lòng thử lại.');
        } finally {
            state.loading = false;
            updateControls();
        }
    }

    async function submitOrder(event) {
        event.preventDefault();
        if (!state.ready || state.loading || state.submitting || state.completed || state.sessionExpired) return;
        state.submitting = true;
        elements.alert.style.display = 'none';
        updateControls();
        const value = id => document.getElementById(id).value.trim();
        const payload = {
            fulfillmentType: elements.fulfillment.value,
            receiverName: value('receiverName'), receiverPhone: value('receiverPhone'),
            deliveryAddress: elements.fulfillment.value === 'DELIVERY' ? value('deliveryAddress') : null,
            paymentMethod: value('paymentMethod'), customerNote: value('customerNote'),
            promotionId: state.promotionId
        };
        let refreshWarning = '';
        let blockOrder = false;
        try {
            const data = await requestApi('/api/orders', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
            });
            if (!data) return;
            if (data.success && data.data?.orderId) {
                state.completed = true;
                clearVoucher();
                alert('Đặt hàng thành công! Mã đơn: ' + data.data.orderId, true);
                setTimeout(() => window.location.assign(`${API_BASE}/orders/${encodeURIComponent(data.data.orderId)}`), 2000);
            } else {
                const errorCode = data.error?.code || '';
                if (errorCode.startsWith('PROMOTION_')) {
                    clearVoucher();
                    updateSummary();
                }
                const orderError = message(data, 'Không thể đặt hàng. Vui lòng thử lại.');
                if (errorCode.startsWith('PROMOTION_') || ['CART_EMPTY', 'FOOD_NOT_AVAILABLE', 'INVALID_OPTION'].includes(errorCode)) {
                    state.ready = false;
                    refreshWarning = orderError;
                    blockOrder = ['FOOD_NOT_AVAILABLE', 'INVALID_OPTION'].includes(errorCode);
                }
                alert(orderError);
            }
        } catch (_) {
            alert('Lỗi kết nối máy chủ. Vui lòng thử lại.');
        } finally {
            state.submitting = false;
            updateControls();
            if (refreshWarning) await loadCheckout(refreshWarning, blockOrder);
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        elements = {
            form: document.getElementById('checkoutForm'), submit: document.getElementById('placeOrderBtn'),
            retry: document.getElementById('retryCheckoutBtn'), alert: document.getElementById('checkoutAlert'),
            fulfillment: document.getElementById('fulfillmentType'),
            subtotal: document.getElementById('summarySubtotal'), fee: document.getElementById('summaryFee'),
            discount: document.getElementById('summaryDiscount'), discountRow: document.getElementById('summaryDiscountRow'),
            total: document.getElementById('summaryTotal')
        };
        elements.fulfillment.addEventListener('change', () => {
            const pickup = elements.fulfillment.value === 'PICKUP';
            document.getElementById('addressGroup').style.display = pickup ? 'none' : 'block';
            document.getElementById('deliveryAddress').required = !pickup;
            updateSummary();
        });
        elements.form.addEventListener('submit', submitOrder);
        elements.retry.addEventListener('click', () => loadCheckout());
        loadCheckout();
    });
})();
