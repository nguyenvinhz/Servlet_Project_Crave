(function () {
    'use strict';

    const API_BASE = document.body.dataset.contextPath || '';
    const VOUCHER_KEY = 'crave_applied_voucher';
    const state = {
        subtotal: 0, discount: 0, promotionId: null,
        policy: null, location: null, locationRevision: 0, locationConfirmed: false, pinDragging: false, addressResolving: false, quote: null, quotePending: false, quoteRequest: 0,
        ready: false, loading: false, submitting: false, completed: false, sessionExpired: false
    };
    let elements;
    let deliveryMap, deliveryMarker;
    let addressLookupTimer;

    function deliveryReady() {
        return elements.fulfillment.value === 'PICKUP' || (state.locationConfirmed && !state.quotePending && state.quote?.deliverable === true);
    }

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
        elements.submit.disabled = !state.ready || !deliveryReady() || state.loading || state.submitting || state.completed || state.sessionExpired;
        elements.retry.hidden = state.ready || state.loading || state.sessionExpired || state.completed;
        elements.form.setAttribute('aria-busy', String(state.loading || state.submitting));
        elements.fulfillment.disabled = state.submitting || state.completed;
        elements.address.disabled = state.submitting || state.completed;
        elements.findAddress.disabled = !deliveryMap || state.addressResolving || state.submitting || state.completed || state.sessionExpired;
        elements.myLocation.disabled = !deliveryMap || state.submitting || state.completed || state.sessionExpired;
        elements.confirmLocation.hidden = !state.location || state.locationConfirmed;
        elements.confirmLocation.disabled = state.pinDragging || state.submitting || state.completed || state.sessionExpired;
        const savedAddress = document.getElementById('savedAddressSelect');
        if (savedAddress) savedAddress.disabled = state.submitting || state.completed;
        if (deliveryMarker?.dragging) {
            if (state.submitting || state.completed || state.sessionExpired) deliveryMarker.dragging.disable();
            else deliveryMarker.dragging.enable();
        }
    }

    function updateSummary() {
        const delivery = elements.fulfillment.value === 'DELIVERY';
        const fee = !delivery || state.subtotal <= 0 ? 0 : (deliveryReady() ? Number(state.quote.deliveryFee) : null);
        const price = amount => new Intl.NumberFormat('vi-VN').format(amount) + ' đ';
        elements.subtotal.textContent = price(state.subtotal);
        elements.fee.textContent = fee != null ? price(fee) : (state.quotePending ? 'Đang tính...' : state.quote?.deliverable === false ? 'Không hỗ trợ' : 'Chưa xác định');
        elements.discount.textContent = '-' + price(state.discount);
        elements.discountRow.hidden = state.discount <= 0;
        elements.total.textContent = price(Math.max(0, state.subtotal + (fee || 0) - state.discount)) + (fee == null ? ' + phí giao hàng' : '');
    }

    function invalidateDeliveryLocation() {
        clearTimeout(addressLookupTimer);
        addressLookupTimer = null;
        state.quoteRequest++;
        state.locationRevision++;
        state.location = null;
        state.locationConfirmed = false;
        state.pinDragging = false;
        state.addressResolving = false;
        state.quote = null;
        state.quotePending = false;
        if (deliveryMarker) { deliveryMarker.remove(); deliveryMarker = null; }
        elements.locationStatus.textContent = 'Đang chờ xác định địa chỉ nhận hàng.';
        elements.matchedAddress.textContent = '';
        elements.searchResults.replaceChildren();
        elements.retryQuote.hidden = true;
        updateSummary();
        updateControls();
    }

    async function resolveDeliveryAddress() {
        clearTimeout(addressLookupTimer);
        addressLookupTimer = null;
        if (!deliveryMap || elements.fulfillment.value !== 'DELIVERY' || state.submitting || state.completed || state.sessionExpired) return;
        const address = elements.address.value.trim();
        if (address.length < 3) {
            elements.locationStatus.textContent = 'Nhập tên đường, địa điểm hoặc tọa độ; bạn cũng có thể chọn trực tiếp trên bản đồ.';
            return;
        }
        invalidateDeliveryLocation();
        const requestId = ++state.quoteRequest;
        state.locationConfirmed = false;
        state.addressResolving = true;
        state.quote = null;
        state.quotePending = true;
        elements.locationStatus.textContent = 'Đang xác định địa chỉ và tính phí giao hàng...';
        elements.retryQuote.hidden = true;
        updateSummary(); updateControls();
        try {
            const data = await requestApi('/api/delivery/search', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ address })
            });
            if (!data || requestId !== state.quoteRequest || address !== elements.address.value.trim() || state.sessionExpired) return;
            if (!data.success || !Array.isArray(data.data) || !data.data.length) throw new Error(message(data, 'Không tìm được địa chỉ. Bạn có thể chọn trực tiếp trên bản đồ.'));
            const candidates = data.data.filter(point => Number.isFinite(point.latitude) && Number.isFinite(point.longitude));
            if (!candidates.length) throw new Error('Chưa xác định được vị trí hợp lệ. Vui lòng chọn trên bản đồ.');
            state.quotePending = false;
            state.addressResolving = false;
            if (candidates.length > 1) {
                elements.locationStatus.textContent = 'Có nhiều kết quả. Chọn địa điểm phù hợp bên dưới rồi kiểm tra điểm nhận trên bản đồ.';
                for (const candidate of candidates) {
                    const button = document.createElement('button');
                    button.type = 'button';
                    button.className = 'button outline delivery-search-result';
                    button.textContent = candidate.label + (candidate.approximate ? ' · Vị trí gợi ý' : '');
                    button.addEventListener('click', () => chooseSearchResult(candidate, true));
                    elements.searchResults.appendChild(button);
                }
                deliveryMap.fitBounds(L.latLngBounds(candidates.map(point => [point.latitude, point.longitude])), { padding: [30, 30], maxZoom: 15 });
            } else await chooseSearchResult(candidates[0]);
        } catch (error) {
            if (requestId === state.quoteRequest && !state.sessionExpired) {
                elements.locationStatus.textContent = error.message || 'Chưa tra được địa chỉ. Vui lòng ghim điểm nhận trên bản đồ.';
                elements.retryQuote.hidden = false;
            }
        } finally {
            if (requestId === state.quoteRequest) { state.addressResolving = false; state.quotePending = false; updateSummary(); updateControls(); }
        }
    }

    function setAddressFromPoint(text) {
        elements.address.value = text.slice(0, 255);
        const saved = document.getElementById('savedAddressSelect');
        if (saved) saved.value = 'custom';
        showAddressHint('Địa chỉ của điểm nhận trên bản đồ. Bạn có thể thêm chi tiết giao hàng ở phần ghi chú.');
    }

    async function chooseSearchResult(candidate, selectedFromList = false) {
        if (state.submitting || state.completed || state.sessionExpired) return;
        const point = [candidate.latitude, candidate.longitude];
        elements.searchResults.replaceChildren();
        elements.matchedAddress.textContent = `Vị trí gợi ý: ${candidate.label}. Hãy kiểm tra và kéo ghim tới đúng nơi nhận hàng.`;
        if (candidate.approximate) elements.matchedAddress.textContent += ' Cần bạn kiểm tra và xác nhận điểm nhận.';
        if (selectedFromList) setAddressFromPoint(`${candidate.label.slice(0, 215)} (${candidate.latitude.toFixed(6)}, ${candidate.longitude.toFixed(6)})`);
        else if (/^[+-]?\d+\.\d+,\s*[+-]?\d+\.\d+$/.test(candidate.label)) setAddressFromPoint(candidate.label);
        deliveryMap.setView(point, 16);
        await selectDeliveryLocation(point, candidate.approximate || selectedFromList ? 'approximate' : 'address');
    }

    async function updateAddressForPin(location, revision) {
        try {
            const data = await requestApi('/api/delivery/reverse', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(location)
            });
            if (!data?.success || !data.data?.label || revision !== state.locationRevision || state.submitting
                    || state.completed || state.sessionExpired || elements.fulfillment.value !== 'DELIVERY') return;
            const coordinates = `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}`;
            // A nearby address is a description. Keep the exact pin visible and authoritative.
            const label = data.data.label === coordinates ? coordinates : `${data.data.label.slice(0, 215)} (${coordinates})`;
            setAddressFromPoint(label);
            elements.matchedAddress.textContent = data.data.label === coordinates
                ? 'Chưa tra được tên địa chỉ. Tọa độ điểm nhận đã được điền; bạn vẫn có thể xác nhận và đặt hàng.'
                : `Địa chỉ gần điểm ghim: ${data.data.label}. Điểm nhận là ghim bạn đã chọn.`;
        } catch (_) {
            // Coordinates are filled immediately; reverse lookup failure must not block checkout.
        }
    }

    async function loadDeliveryPolicy() {
        if (state.policy) return;
        try {
            const data = await requestApi('/api/delivery/policy');
            if (!data) return;
            if (!data.success || !data.data) throw new Error(message(data, 'Không thể tải thông tin giao hàng.'));
            state.policy = data.data;
            const store = data.data.store;
            elements.storeAddress.textContent = `Cửa hàng: ${store.address || 'Chưa thiết lập địa chỉ'}`;
            if (!data.data.configured) throw new Error('Cửa hàng chưa thiết lập vị trí giao hàng. Bạn có thể chọn lấy tại quán.');
            if (!window.L) throw new Error('Không tải được bản đồ. Vui lòng tải lại trang hoặc chọn lấy tại quán.');
            deliveryMap = L.map('deliveryMap').setView([store.latitude, store.longitude], 14);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }).addTo(deliveryMap);
            const storeLabel = document.createElement('span');
            storeLabel.textContent = `CRAVE · ${store.address}`;
            L.marker([store.latitude, store.longitude], { icon: L.divIcon({ className: 'delivery-pin delivery-store-pin', iconSize: [20, 20] }) })
                .addTo(deliveryMap).bindTooltip(storeLabel);
            deliveryMap.on('click', event => selectDeliveryLocation(event.latlng));
            elements.locationStatus.textContent = 'Tìm theo địa chỉ hoặc chạm bản đồ, kéo ghim đến nơi nhận rồi xác nhận điểm nhận.';
            elements.myLocation.hidden = !window.isSecureContext || !navigator.geolocation;
            updateControls();
        } catch (error) {
            elements.locationStatus.textContent = error.message || 'Không thể tải bản đồ giao hàng. Vui lòng thử tải lại trang.';
        }
    }

    async function selectDeliveryLocation(point, source = 'manual') {
        if (!deliveryMap || elements.fulfillment.value !== 'DELIVERY' || state.submitting || state.completed || state.sessionExpired) return;
        const latlng = L.latLng(point);
        clearTimeout(addressLookupTimer);
        addressLookupTimer = null;
        state.quoteRequest++;
        const revision = ++state.locationRevision;
        state.addressResolving = false;
        state.pinDragging = false;
        state.quotePending = false;
        state.quote = null;
        state.locationConfirmed = source === 'address';
        elements.retryQuote.hidden = true;
        if (source === 'manual') elements.matchedAddress.textContent = 'Điểm nhận do bạn chọn trên bản đồ. Hãy kiểm tra ghim và địa chỉ mô tả cùng một nơi.';
        state.location = { latitude: latlng.lat, longitude: latlng.lng };
        elements.searchResults.replaceChildren();
        if (source === 'manual') {
            setAddressFromPoint(`${latlng.lat.toFixed(6)}, ${latlng.lng.toFixed(6)}`);
            updateAddressForPin({ ...state.location }, revision);
        }
        if (deliveryMarker) deliveryMarker.setLatLng(latlng);
        else {
            deliveryMarker = L.marker(latlng, { draggable: true, icon: L.divIcon({ className: 'delivery-pin', iconSize: [24, 24] }) })
                .addTo(deliveryMap).bindTooltip('Điểm nhận hàng');
            deliveryMarker.on('dragstart', () => {
                clearTimeout(addressLookupTimer);
                addressLookupTimer = null;
                state.quoteRequest++; state.quotePending = false; state.addressResolving = false;
                state.locationRevision++;
                state.quote = null; state.locationConfirmed = false; state.pinDragging = true;
                elements.retryQuote.hidden = true;
                elements.locationStatus.textContent = 'Kéo ghim đến đúng nơi nhận hàng rồi xác nhận lại.';
                updateSummary(); updateControls();
            });
            deliveryMarker.on('dragend', () => selectDeliveryLocation(deliveryMarker.getLatLng()));
        }
        if (state.locationConfirmed) await requestDeliveryQuote();
        else {
            elements.locationStatus.textContent = 'Kiểm tra điểm ghim rồi bấm “Xác nhận điểm nhận” để tính phí giao hàng.';
            updateSummary(); updateControls();
        }
    }

    async function requestDeliveryQuote() {
        if (!state.location || !state.locationConfirmed || elements.fulfillment.value !== 'DELIVERY' || state.submitting || state.completed || state.sessionExpired) return;
        const requestId = ++state.quoteRequest;
        const location = { ...state.location };
        state.quote = null;
        state.quotePending = true;
        elements.retryQuote.hidden = true;
        elements.locationStatus.textContent = 'Đang tính phí giao hàng...';
        updateSummary(); updateControls();
        try {
            const data = await requestApi('/api/delivery/quote', {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(location)
            });
            if (!data || requestId !== state.quoteRequest || state.sessionExpired) return;
            if (!data.success || !data.data) throw new Error(message(data, 'Không thể tính phí giao hàng.'));
            state.quote = data.data;
            elements.locationStatus.textContent = !data.data.deliverable
                ? (data.data.message || 'Khu vực này hiện chưa được hỗ trợ giao hàng.')
                : (data.data.message || 'Đã cập nhật phí giao hàng theo điểm nhận.');
        } catch (error) {
            if (requestId === state.quoteRequest && !state.sessionExpired) {
                elements.locationStatus.textContent = error.message || 'Lỗi kết nối khi tính phí giao hàng.';
                elements.retryQuote.hidden = false;
            }
        } finally {
            if (requestId === state.quoteRequest) { state.quotePending = false; updateSummary(); updateControls(); }
        }
    }

    async function requestApi(path, options = {}) {
        const response = await fetch(`${API_BASE}${path}`, {
            ...options,
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json', ...options.headers }
        });
        if (response.status === 401) {
            state.quoteRequest++;
            state.quote = null;
            state.quotePending = false;
            state.addressResolving = false;
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

    function showAddressHint(text) {
        const hint = document.getElementById('addressHint');
        if (hint) hint.textContent = text;
    }

    let addressesLoaded = false;
    async function loadSavedAddresses() {
        if (addressesLoaded) return;
        try {
            const data = await requestApi('/api/addresses');
            if (!data || !data.success || !Array.isArray(data.data)) return;
            const addresses = data.data;
            const selectWrap = document.getElementById('savedAddressWrap');
            const select = document.getElementById('savedAddressSelect');
            const addressInput = document.getElementById('deliveryAddress');
            const noteInput = document.getElementById('customerNote');

            if (!select || !addressInput) return;

            if (addresses.length > 0) {
                select.innerHTML = '';

                let defaultIdx = -1;
                addresses.forEach((addr, idx) => {
                    const opt = document.createElement('option');
                    opt.value = String(idx);
                    const isDef = Boolean(addr.isDefault);
                    if (isDef && defaultIdx === -1) defaultIdx = idx;

                    const notePart = addr.note ? ` (${addr.note})` : '';
                    opt.textContent = `${isDef ? '★ [Mặc định] ' : '📍 '}${addr.addressLine}${notePart}`;
                    select.appendChild(opt);
                });

                const customOpt = document.createElement('option');
                customOpt.value = 'custom';
                customOpt.textContent = '✏️ Nhập địa chỉ khác...';
                select.appendChild(customOpt);

                const chosenIdx = defaultIdx !== -1 ? defaultIdx : 0;
                select.value = String(chosenIdx);

                // Điền địa chỉ mặc định vào ô nhập nếu ô đang trống
                if (!addressInput.value.trim() && addresses[chosenIdx]) {
                    addressInput.value = addresses[chosenIdx].addressLine || '';
                    if (addresses[chosenIdx].note && noteInput && !noteInput.value.trim()) {
                        noteInput.value = addresses[chosenIdx].note;
                    }
                }

                if (selectWrap) {
                    selectWrap.style.display = 'block';
                }

                showAddressHint(addresses[chosenIdx]?.isDefault
                    ? 'Đang dùng địa chỉ mặc định của bạn. Bạn có thể chọn địa chỉ khác ở danh sách hoặc sửa trực tiếp.'
                    : 'Đang dùng địa chỉ từ sổ địa chỉ của bạn.');

                select.addEventListener('change', () => {
                    invalidateDeliveryLocation();
                    if (select.value === 'custom') {
                        addressInput.value = '';
                        addressInput.focus();
                        showAddressHint('Vui lòng nhập địa chỉ giao hàng cho đơn này.');
                    } else {
                        const selectedAddr = addresses[parseInt(select.value, 10)];
                        if (selectedAddr) {
                            addressInput.value = selectedAddr.addressLine || '';
                            if (selectedAddr.note && noteInput && (!noteInput.value.trim() || noteInput.value === addresses[chosenIdx]?.note)) {
                                noteInput.value = selectedAddr.note;
                            }
                            showAddressHint(selectedAddr.isDefault
                                ? 'Đang dùng địa chỉ mặc định của bạn.'
                                : 'Đang dùng địa chỉ từ sổ địa chỉ của bạn.');
                        }
                    }
                    resolveDeliveryAddress();
                });

                addressInput.addEventListener('input', () => {
                    const currentVal = addressInput.value.trim();
                    const matchedIdx = addresses.findIndex(a => a.addressLine.trim() === currentVal);
                    if (matchedIdx !== -1) {
                        select.value = String(matchedIdx);
                        showAddressHint(addresses[matchedIdx].isDefault ? 'Đang dùng địa chỉ mặc định.' : 'Đang dùng địa chỉ đã lưu.');
                    } else {
                        select.value = 'custom';
                        showAddressHint('Địa chỉ tùy chỉnh cho đơn hàng này.');
                    }
                });

                addressesLoaded = true;
            }
        } catch (_) {}
    }

    let profileLoaded = false;
    async function loadProfileInfo() {
        if (profileLoaded) return;
        const nameInput = document.getElementById('receiverName');
        const phoneInput = document.getElementById('receiverPhone');
        if (nameInput?.value && phoneInput?.value) {
            profileLoaded = true;
            return;
        }

        try {
            const data = await requestApi('/api/profile');
            if (data?.success && data.data) {
                if (nameInput && !nameInput.value.trim() && data.data.fullName) {
                    nameInput.value = data.data.fullName;
                }
                if (phoneInput && !phoneInput.value.trim() && data.data.phone) {
                    phoneInput.value = data.data.phone;
                }
                profileLoaded = true;
            }
        } catch (_) {}
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

            // Tải thông tin người nhận và sổ địa chỉ giao hàng
            await Promise.all([loadProfileInfo(), loadSavedAddresses(), loadDeliveryPolicy()]);

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
            if (elements.fulfillment.value === 'DELIVERY') {
                if (state.locationConfirmed) await requestDeliveryQuote();
                else if (!state.location) resolveDeliveryAddress();
            }
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
        if (!state.ready || !deliveryReady() || state.loading || state.submitting || state.completed || state.sessionExpired) return;
        state.submitting = true;
        elements.alert.style.display = 'none';
        updateControls();
        const value = id => document.getElementById(id).value.trim();
        const payload = {
            fulfillmentType: elements.fulfillment.value,
            receiverName: value('receiverName'), receiverPhone: value('receiverPhone'),
            deliveryAddress: elements.fulfillment.value === 'DELIVERY' ? value('deliveryAddress') : null,
            deliveryLatitude: elements.fulfillment.value === 'DELIVERY' ? state.location?.latitude : null,
            deliveryLongitude: elements.fulfillment.value === 'DELIVERY' ? state.location?.longitude : null,
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
                const onlinePayment = ['BANK_TRANSFER', 'E_WALLET'].includes(payload.paymentMethod);
                alert('Đặt hàng thành công! Mã đơn: ' + data.data.orderId
                    + (onlinePayment ? '. Đang chuyển đến mã QR thanh toán...' : ''), true);
                setTimeout(() => window.location.assign(`${API_BASE}/orders/${encodeURIComponent(data.data.orderId)}`), 2000);
            } else {
                const errorCode = data.error?.code || '';
                if (errorCode.startsWith('DELIVERY_')) {
                    state.quote = null;
                    elements.locationStatus.textContent = message(data, 'Vui lòng kiểm tra lại điểm nhận hàng.');
                    elements.retryQuote.hidden = !state.location;
                    updateSummary();
                }
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
            total: document.getElementById('summaryTotal'),
            address: document.getElementById('deliveryAddress'),
            storeAddress: document.getElementById('storeAddress'),
            locationStatus: document.getElementById('deliveryLocationStatus'),
            matchedAddress: document.getElementById('matchedDeliveryAddress'),
            searchResults: document.getElementById('deliverySearchResults'),
            findAddress: document.getElementById('findDeliveryAddressBtn'), myLocation: document.getElementById('useMyLocationBtn'),
            confirmLocation: document.getElementById('confirmDeliveryLocationBtn'),
            retryQuote: document.getElementById('retryDeliveryQuoteBtn')
        };
        elements.fulfillment.addEventListener('change', () => {
            const pickup = elements.fulfillment.value === 'PICKUP';
            document.getElementById('addressGroup').style.display = pickup ? 'none' : 'block';
            document.getElementById('deliveryAddress').required = !pickup;
            state.quoteRequest++;
            state.locationRevision++;
            state.addressResolving = false;
            state.quotePending = false;
            state.quote = null;
            elements.retryQuote.hidden = true;
            if (!pickup) {
                setTimeout(() => deliveryMap?.invalidateSize(), 0);
                if (state.locationConfirmed) requestDeliveryQuote();
                else if (!state.location) resolveDeliveryAddress();
            }
            updateSummary();
            updateControls();
        });
        elements.address.addEventListener('input', () => {
            invalidateDeliveryLocation();
            addressLookupTimer = setTimeout(resolveDeliveryAddress, 1200);
        });
        elements.address.addEventListener('change', resolveDeliveryAddress);
        elements.findAddress.addEventListener('click', resolveDeliveryAddress);
        elements.confirmLocation.addEventListener('click', () => {
            if (!state.location || state.pinDragging || state.submitting || state.completed || state.sessionExpired) return;
            state.locationConfirmed = true;
            requestDeliveryQuote();
        });
        elements.retryQuote.addEventListener('click', () => state.location ? requestDeliveryQuote() : resolveDeliveryAddress());
        elements.myLocation.addEventListener('click', () => {
            if (!navigator.geolocation || state.submitting) return;
            invalidateDeliveryLocation();
            const address = elements.address.value.trim();
            const requestId = state.quoteRequest;
            elements.locationStatus.textContent = 'Đang lấy vị trí hiện tại...';
            navigator.geolocation.getCurrentPosition(position => {
                if (state.submitting || state.completed || state.sessionExpired || requestId !== state.quoteRequest || address !== elements.address.value.trim()) return;
                const point = [position.coords.latitude, position.coords.longitude];
                deliveryMap.setView(point, 17);
                selectDeliveryLocation(point);
            }, () => {
                if (requestId === state.quoteRequest && !state.sessionExpired && !state.completed) elements.locationStatus.textContent = 'Không lấy được vị trí hiện tại. Vui lòng ghim trên bản đồ.';
            }, { enableHighAccuracy: true, maximumAge: 0, timeout: 10000 });
        });
        elements.form.addEventListener('submit', submitOrder);
        elements.retry.addEventListener('click', () => loadCheckout());
        loadCheckout();
    });
})();
