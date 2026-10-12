/**
 * CRAVE - CART & PROMOTION JAVASCRIPT
 * Connects with /api/cart and /api/promotions
 * Handles loading, error, empty states, quantity updates, voucher validation, and checkout.
 */

(function () {
    'use strict';

    // State
    const state = {
        cart: null,
        loading: false,
        error: null,
        appliedVoucher: null, // { code, promotionId, discountAmount, message }
        cartMutation: false,
        voucherPending: false,
        voucherRequest: 0,
        sessionExpired: false,
        activePromotions: [],
        deliveryFee: 15000 // Delivery estimate; pickup is selected at checkout.
    };

    // DOM Elements
    let elements = {};

    const API_BASE = document.body.dataset.contextPath || '';

    function apiErrorMessage(data, fallback) {
        return data?.error?.message || data?.message || fallback;
    }

    async function requestApi(path, options = {}) {
        if (state.sessionExpired) return null;
        const response = await fetch(`${API_BASE}${path}`, {
            ...options,
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json', ...options.headers }
        });
        if (response.status === 401) {
            state.sessionExpired = true;
            state.voucherRequest++;
            state.voucherPending = false;
            state.cart = null;
            state.appliedVoucher = null;
            updateCartBadges(0);
            if (elements.itemsContainer) elements.itemsContainer.replaceChildren();
            showErrorState('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
            try { sessionStorage.removeItem('crave_applied_voucher'); } catch (_) { }
            const query = new URLSearchParams({
                reason: 'session-expired',
                returnTo: `${window.location.pathname}${window.location.search}`
            });
            window.location.assign(`${API_BASE}/auth/login?${query}`);
            return null;
        }
        if (state.sessionExpired) return null;
        const data = await response.json();
        if (state.sessionExpired) return null;
        if (!response.ok) data.success = false;
        return data;
    }

    document.addEventListener('DOMContentLoaded', () => {
        cacheDOMElements();
        initCart();
        setupEventListeners();
    });

    function cacheDOMElements() {
        elements = {
            cartLoading: document.getElementById('cartLoadingState'),
            cartError: document.getElementById('cartErrorState'),
            cartEmpty: document.getElementById('cartEmptyState'),
            cartContent: document.getElementById('cartContentWrapper'),
            itemsContainer: document.getElementById('cartItemsList'),
            subtotalEl: document.getElementById('summarySubtotal'),
            deliveryFeeEl: document.getElementById('summaryDeliveryFee'),
            discountRowEl: document.getElementById('summaryDiscountRow'),
            discountEl: document.getElementById('summaryDiscount'),
            totalEl: document.getElementById('summaryTotal'),
            cartCountBadges: document.querySelectorAll('.cart-badge-count'),
            voucherInput: document.getElementById('voucherCodeInput'),
            voucherApplyBtn: document.getElementById('voucherApplyBtn'),
            voucherAppliedChip: document.getElementById('voucherAppliedChip'),
            voucherAppliedCode: document.getElementById('voucherAppliedCode'),
            voucherAppliedDiscount: document.getElementById('voucherAppliedDiscount'),
            voucherRemoveBtn: document.getElementById('voucherRemoveBtn'),
            voucherFeedback: document.getElementById('voucherFeedback'),
            viewPromosBtn: document.getElementById('viewPromosBtn'),
            promosModal: document.getElementById('promosModal'),
            promosModalList: document.getElementById('promosModalList'),
            promosModalClose: document.getElementById('promosModalClose'),
            checkoutBtn: document.getElementById('proceedCheckoutBtn'),
            clearCartBtn: document.getElementById('clearCartBtn'),
            retryBtn: document.getElementById('retryFetchCartBtn')
        };
    }

    function setupEventListeners() {
        if (elements.voucherApplyBtn) {
            elements.voucherApplyBtn.addEventListener('click', handleApplyVoucher);
        }
        if (elements.voucherInput) {
            elements.voucherInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    handleApplyVoucher();
                }
            });
        }
        if (elements.voucherRemoveBtn) {
            elements.voucherRemoveBtn.addEventListener('click', handleRemoveVoucher);
        }
        if (elements.viewPromosBtn) {
            elements.viewPromosBtn.addEventListener('click', openPromotionsModal);
        }
        if (elements.promosModalClose) {
            elements.promosModalClose.addEventListener('click', closePromotionsModal);
        }
        if (elements.promosModal) {
            elements.promosModal.addEventListener('click', (e) => {
                if (e.target === elements.promosModal) {
                    closePromotionsModal();
                }
            });
        }
        if (elements.clearCartBtn) {
            elements.clearCartBtn.addEventListener('click', handleClearCart);
        }
        if (elements.retryBtn) {
            elements.retryBtn.addEventListener('click', () => {
                state.error = null;
                initCart();
            });
        }
        if (elements.checkoutBtn) {
            elements.checkoutBtn.addEventListener('click', handleProceedCheckout);
        }
    }

    /* ==========================================================================
       API INTEGRATION
       ========================================================================== */

    async function initCart() {
        if (state.sessionExpired || state.loading) return;
        state.loading = true;
        showLoadingState();
        updateControls();
        try {
            const data = await requestApi('/api/cart');
            if (!data) return;

            if (data.success && data.data) {
                state.cart = data.data;
                state.loading = false;
                state.error = null;
                renderCart();
                checkUrlVoucherParam();
            } else {
                showErrorState(apiErrorMessage(data, "Không thể tải giỏ hàng."));
            }
        } catch (err) {
            console.error("Cart fetch error:", err);
            showErrorState("Lỗi kết nối máy chủ khi lấy giỏ hàng. Vui lòng kiểm tra lại đường truyền.");
        } finally {
            state.loading = false;
            updateControls();
        }
    }

    function checkUrlVoucherParam() {
        const urlParams = new URLSearchParams(window.location.search);
        const code = urlParams.get('voucher') || urlParams.get('code');
        if (code && elements.voucherInput) {
            elements.voucherInput.value = code.trim();
            handleApplyVoucher();
        }
    }

    async function updateItemQuantity(cartItemId, newQty) {
        if (cartBusy()) return;
        if (newQty < 0) return;
        if (newQty === 0) {
            if (confirm("Bạn có chắc chắn muốn xóa món này khỏi giỏ hàng?")) {
                removeItem(cartItemId);
            }
            return;
        }

        state.cartMutation = true;
        updateControls();
        try {
            const data = await requestApi('/api/cart/items/update', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({ cartItemId: cartItemId, quantity: newQty })
            });
            if (!data) return;

            if (data.success && data.data) {
                state.cart = data.data;
                renderCart();
                await revalidateAppliedVoucher();
                showToast("Đã cập nhật số lượng món", "success");
            } else {
                const msg = apiErrorMessage(data, "Không thể cập nhật số lượng");
                showToast(msg, "error");
            }
        } catch (err) {
            showToast("Lỗi kết nối khi cập nhật món", "error");
        } finally {
            state.cartMutation = false;
            updateControls();
        }
    }

    async function removeItem(cartItemId) {
        if (cartBusy()) return;
        state.cartMutation = true;
        updateControls();
        try {
            const data = await requestApi(`/api/cart/items?cartItemId=${encodeURIComponent(cartItemId)}`, {
                method: 'DELETE',
                headers: { 'Accept': 'application/json' }
            });
            if (!data) return;

            if (data.success && data.data) {
                state.cart = data.data;
                renderCart();
                await revalidateAppliedVoucher();
                showToast("Đã xóa món khỏi giỏ hàng", "success");
            } else {
                const msg = apiErrorMessage(data, "Không thể xóa món");
                showToast(msg, "error");
            }
        } catch (err) {
            showToast("Lỗi kết nối khi xóa món", "error");
        } finally {
            state.cartMutation = false;
            updateControls();
        }
    }

    async function handleClearCart() {
        if (cartBusy()) return;
        if (!confirm("Bạn có chắc muốn xóa toàn bộ món trong giỏ hàng?")) {
            return;
        }

        state.cartMutation = true;
        updateControls();
        try {
            const data = await requestApi('/api/cart/clear', {
                method: 'POST',
                headers: { 'Accept': 'application/json' }
            });
            if (!data) return;

            if (data.success && data.data) {
                state.cart = data.data;
                state.appliedVoucher = null;
                clearStoredVoucher();
                renderCart();
                showToast("Đã làm sạch giỏ hàng", "success");
            } else {
                showToast(apiErrorMessage(data, "Không thể xóa giỏ hàng"), "error");
            }
        } catch (err) {
            showToast("Lỗi kết nối khi xóa giỏ hàng", "error");
        } finally {
            state.cartMutation = false;
            updateControls();
        }
    }

    async function handleApplyVoucher() {
        if (cartBusy()) return;
        const code = elements.voucherInput ? elements.voucherInput.value.trim().toUpperCase() : '';
        if (!code) {
            setVoucherFeedback("Vui lòng nhập mã khuyến mãi", "error");
            return;
        }

        if (!state.cart || !state.cart.subtotal || state.cart.subtotal <= 0) {
            setVoucherFeedback("Giỏ hàng chưa có món để áp dụng khuyến mãi", "error");
            return;
        }

        // Set button loading
        const requestId = ++state.voucherRequest;
        state.voucherPending = true;
        setButtonLoading(elements.voucherApplyBtn, true);
        setVoucherFeedback("", "");
        updateControls();

        try {
            const data = await requestApi('/api/promotions/validate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({
                    code: code,
                    subtotal: state.cart.subtotal
                })
            });
            if (requestId !== state.voucherRequest) return;
            if (!data) return;

            if (data.success && data.data && data.data.valid) {
                state.appliedVoucher = {
                    code: code,
                    promotionId: data.data.promotion?.promotionId || null,
                    discountAmount: data.data.discountAmount || 0,
                    message: data.data.message || "Áp dụng mã thành công!"
                };
                renderSummary();
                showToast(`Áp dụng mã ${code} thành công!`, "success");
                setVoucherFeedback("", "");
            } else {
                const msg = data.data?.message || apiErrorMessage(data, "Mã khuyến mãi không hợp lệ hoặc đã hết hạn");
                setVoucherFeedback(msg, "error");
            }
        } catch (err) {
            if (requestId !== state.voucherRequest) return;
            setVoucherFeedback("Lỗi kết nối khi xác thực mã khuyến mãi", "error");
        } finally {
            if (requestId === state.voucherRequest) {
                state.voucherPending = false;
                setButtonLoading(elements.voucherApplyBtn, false);
                updateControls();
            }
        }
    }

    function handleRemoveVoucher() {
        state.voucherRequest++;
        state.voucherPending = false;
        setButtonLoading(elements.voucherApplyBtn, false);
        state.appliedVoucher = null;
        clearStoredVoucher();
        if (elements.voucherInput) {
            elements.voucherInput.value = '';
        }
        setVoucherFeedback("", "");
        renderSummary();
        updateControls();
        showToast("Đã hủy áp dụng mã ưu đãi", "info");
    }

    async function revalidateAppliedVoucher() {
        if (!state.appliedVoucher || !state.cart || !state.cart.subtotal || state.cart.subtotal <= 0) {
            state.appliedVoucher = null;
            clearStoredVoucher();
            renderSummary();
            return;
        }

        const code = state.appliedVoucher.code;
        const requestId = ++state.voucherRequest;
        state.voucherPending = true;
        // The previous amount belongs to the old cart and cannot be displayed as current.
        state.appliedVoucher.discountAmount = 0;
        renderSummary();
        updateControls();
        try {
            const data = await requestApi('/api/promotions/validate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    code: code,
                    subtotal: state.cart.subtotal
                })
            });
            if (requestId !== state.voucherRequest) return;
            if (!data) return;
            if (data.success && data.data && data.data.valid) {
                state.appliedVoucher.discountAmount = data.data.discountAmount;
                state.appliedVoucher.promotionId = data.data.promotion?.promotionId || null;
            } else {
                showToast(`Mã ${code} đã tự động hủy do đơn hàng không còn đủ điều kiện`, "error");
                state.appliedVoucher = null;
                clearStoredVoucher();
            }
        } catch (err) {
            if (requestId !== state.voucherRequest) return;
            state.appliedVoucher = null;
            clearStoredVoucher();
            setVoucherFeedback("Không thể xác thực lại voucher. Vui lòng áp dụng lại mã trước khi thanh toán.", "error");
        } finally {
            if (requestId === state.voucherRequest) {
                state.voucherPending = false;
                renderSummary();
                updateControls();
            }
        }
    }

    async function openPromotionsModal() {
        if (!elements.promosModal) return;
        elements.promosModal.classList.add('active');

        if (elements.promosModalList) {
            elements.promosModalList.innerHTML = `
                <div style="text-align: center; padding: 24px; color: var(--crave-text-muted);">
                    <div class="btn-spinner" style="border-top-color: var(--crave-brand); border-color: rgba(244,81,30,0.2); width: 24px; height: 24px; margin-bottom: 8px;"></div>
                    <p>Đang tải danh sách khuyến mãi khả dụng...</p>
                </div>
            `;
        }

        try {
            const data = await requestApi('/api/promotions/active', {
                headers: { 'Accept': 'application/json' }
            });
            if (!data) return;

            if (!data.success) {
                elements.promosModalList.textContent = apiErrorMessage(data, 'Không thể tải danh sách khuyến mãi.');
            } else if (Array.isArray(data.data) && data.data.length > 0) {
                state.activePromotions = data.data;
                renderPromotionsModalList(data.data);
            } else {
                elements.promosModalList.innerHTML = `
                    <div style="text-align: center; padding: 24px; color: var(--crave-text-muted);">
                        <p>Hiện không có mã khuyến mãi công khai nào.</p>
                    </div>
                `;
            }
        } catch (err) {
            elements.promosModalList.innerHTML = `
                <div style="text-align: center; padding: 24px; color: var(--crave-danger);">
                    <p>Không thể tải danh sách khuyến mãi.</p>
                </div>
            `;
        }
    }

    function closePromotionsModal() {
        if (elements.promosModal) {
            elements.promosModal.classList.remove('active');
        }
    }

    function renderPromotionsModalList(promos) {
        if (!elements.promosModalList) return;
        elements.promosModalList.innerHTML = promos.map(p => {
            const minOrderText = p.minimumOrderValue > 0
                ? `Đơn tối thiểu ${formatPrice(p.minimumOrderValue)}`
                : 'Mọi đơn hàng';
            const discountDesc = p.discountType === 'PERCENT'
                ? `Giảm ${p.discountValue}% ${p.maximumDiscount > 0 ? `(tối đa ${formatPrice(p.maximumDiscount)})` : ''}`
                : `Giảm ${formatPrice(p.discountValue)}`;

            return `
                <div class="promo-card">
                    <div>
                        <span class="promo-badge-tag">${escapeHtml(p.code)}</span>
                        <h4 class="promo-card-title">${escapeHtml(p.name || discountDesc)}</h4>
                        <p class="promo-card-desc">${discountDesc} · ${minOrderText}</p>
                    </div>
                    <button type="button" class="btn-apply-promo-card" data-code="${escapeHtml(p.code)}">
                        Áp dụng
                    </button>
                </div>
            `;
        }).join('');

        // Attach apply click events
        elements.promosModalList.querySelectorAll('.btn-apply-promo-card').forEach(btn => {
            btn.addEventListener('click', () => {
                const code = btn.getAttribute('data-code');
                if (code && elements.voucherInput) {
                    elements.voucherInput.value = code;
                    closePromotionsModal();
                    handleApplyVoucher();
                }
            });
        });
    }

    function handleProceedCheckout() {
        if (cartBusy()) return;
        if (!state.cart || !state.cart.items || state.cart.items.length === 0) {
            showToast("Giỏ hàng của bạn đang trống!", "error");
            return;
        }
        let target = `${API_BASE}/checkout`;
        try {
            if (state.appliedVoucher) {
                sessionStorage.setItem('crave_applied_voucher', JSON.stringify(state.appliedVoucher));
            } else {
                sessionStorage.removeItem('crave_applied_voucher');
            }
        } catch (_) {
            if (state.appliedVoucher) {
                target += `?${new URLSearchParams({ voucher: state.appliedVoucher.code })}`;
            }
        }
        window.location.href = target;
    }

    /* ==========================================================================
       RENDERING & STATE TRANSITIONS
       ========================================================================== */

    function showLoadingState() {
        if (elements.cartLoading) elements.cartLoading.style.display = 'block';
        if (elements.cartError) elements.cartError.style.display = 'none';
        if (elements.cartEmpty) elements.cartEmpty.style.display = 'none';
        if (elements.cartContent) elements.cartContent.style.display = 'none';
    }

    function showErrorState(message) {
        state.error = message;
        updateControls();
        if (elements.cartLoading) elements.cartLoading.style.display = 'none';
        if (elements.cartError) elements.cartError.style.display = 'block';
        if (elements.cartEmpty) elements.cartEmpty.style.display = 'none';
        if (elements.cartContent) elements.cartContent.style.display = 'none';
        const msgEl = document.getElementById('cartErrorMsg');
        if (msgEl) msgEl.textContent = message;
    }

    function renderCart() {
        if (elements.cartLoading) elements.cartLoading.style.display = 'none';
        if (elements.cartError) elements.cartError.style.display = 'none';

        const items = state.cart ? state.cart.items : [];
        updateCartBadges(state.cart ? state.cart.totalItems : 0);

        if (!items || items.length === 0) {
            state.appliedVoucher = null;
            clearStoredVoucher();
            renderSummary();
            updateControls();
            // Render Empty State
            if (elements.cartEmpty) elements.cartEmpty.style.display = 'block';
            if (elements.cartContent) elements.cartContent.style.display = 'none';
            return;
        }

        // Render Populated State
        if (elements.cartEmpty) elements.cartEmpty.style.display = 'none';
        if (elements.cartContent) elements.cartContent.style.display = 'grid';

        renderItemsList(items);
        renderSummary();
        updateControls();
    }

    function renderItemsList(items) {
        if (!elements.itemsContainer) return;

        elements.itemsContainer.innerHTML = items.map(item => {
            const optionsText = formatOptionsText(item.options);
            const linePriceFormatted = formatPrice(item.unitPrice || item.basePrice || 0);

            // Screenshot format: "$12.90 × 1" in orange
            const priceTagText = `${linePriceFormatted} × ${item.quantity}`;

            const imageUrl = item.foodImageUrl
                ? (item.foodImageUrl.startsWith('http') || item.foodImageUrl.startsWith('/')
                    ? item.foodImageUrl
                    : `${API_BASE}/${item.foodImageUrl}`)
                : `${API_BASE}/assets/images/food-placeholder.jpg`;

            return `
                <div class="cart-item-card" data-id="${escapeHtml(item.cartItemId)}">
                    <div class="cart-item-image-wrapper">
                        <img src="${escapeHtml(imageUrl)}" alt="${escapeHtml(item.foodName || 'Food')}" class="cart-item-image"
                             onerror="this.src='https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=200&auto=format&fit=crop&q=80'">
                    </div>
                    <div class="cart-item-info">
                        <h3 class="cart-item-title">${escapeHtml(item.foodName || 'Món ăn')}</h3>
                        ${optionsText ? `<p class="cart-item-options">${escapeHtml(optionsText)}</p>` : ''}
                        ${item.note ? `<p class="cart-item-note">💬 "${escapeHtml(item.note)}"</p>` : ''}
                        <div class="cart-item-actions">
                            <div class="cart-stepper">
                                <button type="button" class="cart-stepper-btn btn-dec" data-id="${escapeHtml(item.cartItemId)}" data-qty="${item.quantity - 1}" aria-label="Giảm số lượng">−</button>
                                <span class="cart-stepper-val">${item.quantity}</span>
                                <button type="button" class="cart-stepper-btn btn-inc" data-id="${escapeHtml(item.cartItemId)}" data-qty="${item.quantity + 1}" aria-label="Tăng số lượng">+</button>
                            </div>
                            <button type="button" class="cart-item-remove-btn btn-remove" data-id="${escapeHtml(item.cartItemId)}" title="Xóa món khỏi giỏ">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <polyline points="3 6 5 6 21 6"></polyline>
                                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                </svg>
                            </button>
                        </div>
                    </div>
                    <div class="cart-item-price-tag">
                        ${priceTagText}
                    </div>
                </div>
            `;
        }).join('');

        // Attach stepper events
        elements.itemsContainer.querySelectorAll('.btn-dec').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.getAttribute('data-id');
                const qty = parseInt(btn.getAttribute('data-qty'), 10);
                updateItemQuantity(id, qty);
            });
        });

        elements.itemsContainer.querySelectorAll('.btn-inc').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.getAttribute('data-id');
                const qty = parseInt(btn.getAttribute('data-qty'), 10);
                updateItemQuantity(id, qty);
            });
        });

        elements.itemsContainer.querySelectorAll('.btn-remove').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.getAttribute('data-id');
                removeItem(id);
            });
        });
    }

    function renderSummary() {
        const subtotal = state.cart ? parseFloat(state.cart.subtotal || 0) : 0;
        const deliveryFee = subtotal > 0 ? state.deliveryFee : 0;
        const discount = state.appliedVoucher ? parseFloat(state.appliedVoucher.discountAmount || 0) : 0;
        const total = Math.max(0, subtotal + deliveryFee - discount);

        if (elements.subtotalEl) elements.subtotalEl.textContent = formatPrice(subtotal);
        if (elements.deliveryFeeEl) {
            elements.deliveryFeeEl.textContent = formatPrice(deliveryFee);
        }

        // Discount row
        if (elements.discountRowEl && elements.discountEl) {
            if (discount > 0) {
                elements.discountRowEl.style.display = 'flex';
                elements.discountEl.textContent = `-${formatPrice(discount)}`;
            } else {
                elements.discountRowEl.style.display = 'none';
            }
        }

        if (elements.totalEl) elements.totalEl.textContent = formatPrice(total);

        // Voucher applied chip
        if (elements.voucherAppliedChip) {
            if (state.appliedVoucher) {
                elements.voucherAppliedChip.style.display = 'flex';
                if (elements.voucherAppliedCode) elements.voucherAppliedCode.textContent = state.appliedVoucher.code;
                if (elements.voucherAppliedDiscount) {
                    elements.voucherAppliedDiscount.textContent = `-${formatPrice(state.appliedVoucher.discountAmount)}`;
                }
                if (elements.voucherInput) elements.voucherInput.style.display = 'none';
                if (elements.voucherApplyBtn) elements.voucherApplyBtn.style.display = 'none';
            } else {
                elements.voucherAppliedChip.style.display = 'none';
                if (elements.voucherInput) elements.voucherInput.style.display = 'block';
                if (elements.voucherApplyBtn) elements.voucherApplyBtn.style.display = 'inline-flex';
            }
        }
    }

    function updateCartBadges(count) {
        if (!elements.cartCountBadges) return;
        elements.cartCountBadges.forEach(badge => {
            badge.textContent = count > 0 ? count : '0';
            badge.style.display = count > 0 ? 'inline-flex' : 'none';
        });
    }

    function clearStoredVoucher() {
        try { sessionStorage.removeItem('crave_applied_voucher'); } catch (_) { }
    }

    function cartBusy() {
        return state.loading || state.cartMutation || state.voucherPending || state.sessionExpired || Boolean(state.error);
    }

    function updateControls() {
        const busy = cartBusy();
        if (elements.checkoutBtn) elements.checkoutBtn.disabled = busy || !state.cart?.items?.length;
        if (elements.clearCartBtn) elements.clearCartBtn.disabled = busy;
        if (elements.voucherApplyBtn) elements.voucherApplyBtn.disabled = busy;
        if (elements.retryBtn) elements.retryBtn.disabled = state.loading || state.sessionExpired;
        if (elements.itemsContainer) {
            elements.itemsContainer.querySelectorAll('button').forEach(button => button.disabled = busy);
        }
    }

    /* ==========================================================================
       HELPERS & UTILITIES
       ========================================================================== */

    function formatPrice(amount) {
        const num = parseFloat(amount || 0);
        return new Intl.NumberFormat('vi-VN').format(num) + ' đ';
    }

    function formatOptionsText(options) {
        if (!options || !Array.isArray(options) || options.length === 0) {
            return '';
        }
        return options.map(o => o.name || o.optionName).filter(Boolean).join(' · ');
    }

    function setVoucherFeedback(msg, type) {
        if (!elements.voucherFeedback) return;
        elements.voucherFeedback.textContent = msg;
        elements.voucherFeedback.className = `voucher-feedback ${type}`;
        elements.voucherFeedback.style.display = msg ? 'block' : 'none';
    }

    function setButtonLoading(btn, isLoading) {
        if (!btn) return;
        if (isLoading) {
            btn.disabled = true;
            btn.setAttribute('data-original-text', btn.innerHTML);
            btn.innerHTML = '<span class="btn-spinner"></span>';
        } else {
            btn.disabled = false;
            const original = btn.getAttribute('data-original-text');
            if (original) btn.innerHTML = original;
        }
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
        toast.innerHTML = `<span>${escapeHtml(message)}</span>`;
        container.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(10px)';
            toast.style.transition = 'all 0.25s ease';
            setTimeout(() => toast.remove(), 250);
        }, 3200);
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

})();
