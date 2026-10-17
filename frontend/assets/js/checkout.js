document.addEventListener('DOMContentLoaded', () => {
    'use strict';
    const form = document.getElementById('checkoutForm');
    if (!form) return;
    const byId = id => document.getElementById(id), context = document.body.dataset.contextPath || '';
    const money = amount => Number(amount || 0).toLocaleString('vi-VN') + '\u00a0đ';
    const type = byId('fulfillmentType'), address = byId('deliveryAddress'), place = byId('placeOrderBtn');
    const status = byId('deliveryLocationStatus'), searchResults = byId('deliverySearchResults');
    const lat = byId('deliveryLatitude'), lon = byId('deliveryLongitude');
    const locate = byId('useMyLocationBtn'), confirm = byId('confirmDeliveryLocationBtn');
    const savedAddresses = byId('savedAddressSelect'), note = byId('customerNote');
    const requests = new Set();
    let map, marker, fee = null, confirmed = false, revision = 0;
    let searching = false, quoting = false, locating = false, undeliverable = false;
    let searchAddress = '', selectedAddress = '', resolvedAddress = '', lastSearch = '', lastSavedNote = '', lookupTimer;
    const subtotal = Number(form.dataset.subtotal), discount = Number(form.dataset.discount);
    const empty = form.dataset.empty === 'true';
    function summary() {
        const pickup = type.value === 'PICKUP', amount = pickup || subtotal <= 0 ? 0 : fee;
        const hasPoint = Boolean(marker && lat.value && lon.value);
        byId('summaryFee').textContent = amount != null ? money(amount)
            : quoting ? 'Đang tính...' : undeliverable ? 'Không hỗ trợ' : 'Chưa xác định';
        byId('summaryDiscount').textContent = '-' + money(discount);
        byId('summaryDiscountRow').hidden = discount <= 0;
        byId('summaryTotal').textContent = money(Math.max(0, subtotal - discount + (amount || 0)))
            + (amount == null ? ' + phí giao hàng' : '');
        place.disabled = empty || (!pickup && (!confirmed || fee == null));
        confirm.hidden = pickup || !hasPoint || confirmed;
        confirm.disabled = quoting;
        byId('retryDeliveryQuoteBtn').disabled = quoting || !hasPoint;
        byId('findDeliveryAddressBtn').disabled = pickup || !map || searching || address.value.trim().length < 3;
        locate.disabled = locating;
    }
    function invalidate(clearPoint) {
        clearTimeout(lookupTimer); lookupTimer = null;
        revision++;
        requests.forEach(request => request.abort());
        requests.clear();
        searching = false; quoting = false; locating = false;
        fee = null; confirmed = false; undeliverable = false;
        byId('retryDeliveryQuoteBtn').hidden = true;
        searchResults.replaceChildren();
        if (clearPoint) {
            lat.value = ''; lon.value = ''; selectedAddress = ''; resolvedAddress = '';
            if (marker) { map.removeLayer(marker); marker = null; }
            byId('matchedDeliveryAddress').textContent = '';
        }
    }
    async function post(path, values) {
        const controller = new AbortController();
        requests.add(controller);
        let timedOut = false;
        const timer = setTimeout(() => { timedOut = true; controller.abort(); }, 15000);
        try {
            const response = await fetch(context + path, {
                method: 'POST', credentials: 'same-origin', signal: controller.signal,
                headers: {'Accept': 'text/html', 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'},
                body: new URLSearchParams(values)
            });
            if (response.redirected && new URL(response.url).pathname.includes('/auth/login')) {
                location.href = response.url; throw new Error('Vui lòng đăng nhập lại.');
            }
            const document = new DOMParser().parseFromString(await response.text(), 'text/html');
            if (!response.ok) throw new Error(document.querySelector('[data-delivery-error]')?.textContent.trim()
                || 'Chưa xử lý được vị trí. Vui lòng thử lại hoặc chọn điểm trên bản đồ.');
            return document;
        } catch (error) {
            if (timedOut) throw new Error('Tìm vị trí mất quá lâu. Vui lòng thử lại hoặc ghim điểm nhận trên bản đồ.');
            if (error.name === 'TypeError') throw new Error('Không kết nối được máy chủ. Vui lòng kiểm tra kết nối và thử lại.');
            throw error;
        } finally {
            clearTimeout(timer); requests.delete(controller);
        }
    }
    async function quote() {
        if (!marker || !lat.value || !lon.value || type.value !== 'DELIVERY' || quoting) return;
        invalidate(false);
        const current = revision;
        quoting = true; summary(); status.textContent = 'Đang tính phí giao hàng...';
        try {
            const document = await post('/delivery/quote', {latitude:lat.value, longitude:lon.value});
            if (current !== revision || type.value !== 'DELIVERY') return;
            const result = document.querySelector('[data-quote]');
            if (!result) throw new Error('Chưa tính được phí giao hàng.');
            confirmed = result.dataset.deliverable === 'true';
            undeliverable = !confirmed;
            fee = confirmed ? Number(result.dataset.fee) : null;
            if (confirmed && (!Number.isFinite(fee) || fee < 0)) throw new Error('Chưa tính được phí giao hàng.');
            status.textContent = result.textContent;
        } catch (error) {
            if (current !== revision) return;
            confirmed = false; fee = null;
            status.textContent = error.message; byId('retryDeliveryQuoteBtn').hidden = false;
        } finally {
            if (current === revision) { quoting = false; summary(); }
        }
    }
    function writeAddress(label, point) {
        const coordinates = Number(point.lat).toFixed(6) + ', ' + Number(point.lng).toFixed(6);
        const suffix = ' (' + coordinates + ')';
        const text = (label || '').trim();
        address.value = !text || /^[+-]?[\d.]+\s*[,;]\s*[+-]?[\d.]+$/.test(text)
            ? coordinates : text.slice(0, 255 - suffix.length) + suffix;
        resolvedAddress = address.value;
    }
    function selectPoint(point, label, originalAddress) {
        if (!map || type.value !== 'DELIVERY' || !Number.isFinite(point.lat) || !Number.isFinite(point.lng)) return;
        invalidate(false);
        selectedAddress = originalAddress || '';
        lat.value = Number(point.lat).toFixed(7); lon.value = Number(point.lng).toFixed(7);
        if (marker) marker.setLatLng(point);
        else {
            marker = L.marker(point, {draggable:true, icon:L.divIcon({className:'delivery-pin', iconSize:[24,24]})})
                .addTo(map).bindTooltip('Điểm nhận hàng');
            marker.on('dragstart', () => { invalidate(false); lat.value = ''; lon.value = ''; summary(); });
            marker.on('dragend', () => selectPoint(marker.getLatLng(), null, selectedAddress));
        }
        writeAddress(selectedAddress || label, point);
        byId('matchedDeliveryAddress').textContent = label || 'Điểm nhận do bạn chọn trên bản đồ. Hãy kiểm tra ghim rồi xác nhận.';
        status.textContent = 'Đây là vị trí gợi ý. Kiểm tra và kéo ghim đúng nơi nhận hàng trước khi xác nhận.';
        summary();
        if (!label) {
            const current = revision;
            post('/delivery/reverse', {latitude:lat.value, longitude:lon.value}).then(document => {
                const result = document.querySelector('[data-location-label]');
                if (result && current === revision) {
                    byId('matchedDeliveryAddress').textContent = result.textContent;
                    if (!selectedAddress) writeAddress(result.textContent, point);
                }
            }).catch(() => {});
        }
    }
    function chooseResult(button, chosenFromList) {
        const point = L.latLng(Number(button.dataset.latitude), Number(button.dataset.longitude));
        const approximate = button.dataset.approximate !== 'false';
        selectPoint(point, button.textContent.trim(), searchAddress);
        map.setView(point, 16);
        // A suggested street position still needs the customer to confirm the receiving pin.
        if (!approximate && !chosenFromList) quote();
    }
    async function search() {
        clearTimeout(lookupTimer); lookupTimer = null;
        const query = address.value.trim();
        if (!map || type.value !== 'DELIVERY' || searching || query.length < 3) return;
        if (marker && query === resolvedAddress) return;
        invalidate(true);
        const current = revision;
        lastSearch = query;
        searching = true; summary(); status.textContent = 'Đang tìm địa chỉ...';
        try {
            const document = await post('/delivery/search', {address:query});
            if (current !== revision || type.value !== 'DELIVERY') return;
            searchAddress = query;
            const candidates = Array.from(document.querySelectorAll('.delivery-search-result')).filter(button =>
                Number.isFinite(Number(button.dataset.latitude)) && Number.isFinite(Number(button.dataset.longitude)));
            if (!candidates.length) throw new Error('Chưa tìm thấy địa chỉ. Hãy thêm phường/thành phố hoặc chọn điểm trên bản đồ.');
            if (candidates.length === 1) chooseResult(candidates[0], false);
            else {
                searchResults.replaceChildren(...candidates);
                map.fitBounds(L.latLngBounds(candidates.map(button => [Number(button.dataset.latitude), Number(button.dataset.longitude)])),
                    {padding:[30,30], maxZoom:15});
                status.textContent = 'Có nhiều kết quả. Chọn địa điểm phù hợp bên dưới rồi kiểm tra điểm nhận trên bản đồ.';
            }
        } catch (error) {
            if (current === revision) status.textContent = error.message;
        } finally {
            if (current === revision) { searching = false; summary(); }
        }
    }
    if (form.dataset.configured === 'true' && window.L) {
        const store = [Number(form.dataset.storeLatitude), Number(form.dataset.storeLongitude)];
        map = L.map('deliveryMap').setView(store, 14);
        L.tileLayer(context + '/map/tiles/{z}/{x}/{y}.png', {
            maxZoom:19, attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);
        const storeLabel = document.createElement('span');
        storeLabel.textContent = 'CRAVE · ' + byId('storeAddress').textContent.replace(/^Cửa hàng:\s*/, '');
        L.marker(store, {icon:L.divIcon({className:'delivery-pin delivery-store-pin', iconSize:[20,20]})})
            .addTo(map).bindTooltip(storeLabel);
        map.on('click', event => selectPoint(event.latlng));
        status.textContent = 'Tìm theo địa chỉ hoặc chạm bản đồ, kéo ghim đến nơi nhận rồi xác nhận điểm nhận.';
    } else {
        status.textContent = 'Chưa mở được bản đồ nhận hàng. Bạn có thể tải lại trang hoặc chọn lấy tại quán.';
    }
    if (empty) {
        byId('checkoutAlert').textContent = 'Giỏ hàng đang trống. Vui lòng chọn món trước khi đặt hàng.';
        byId('checkoutAlert').classList.add('error');
        byId('checkoutAlert').style.display = 'block';
    }
    searchResults.addEventListener('click', event => {
        const button = event.target.closest('[data-latitude]');
        if (!button || !map) return;
        chooseResult(button, true);
    });
    byId('findDeliveryAddressBtn').addEventListener('click', search);
    address.addEventListener('keydown', event => {
        if (event.key === 'Enter') { event.preventDefault(); search(); }
    });
    confirm.addEventListener('click', quote);
    byId('retryDeliveryQuoteBtn').addEventListener('click', quote);
    byId('retryCheckoutBtn').addEventListener('click', () => location.reload());
    function savedAddressHint() {
        const selected = savedAddresses.selectedOptions[0];
        byId('addressHint').textContent = selected?.dataset.default === 'true'
            ? 'Đang dùng địa chỉ mặc định của bạn. Bạn có thể chọn địa chỉ khác ở danh sách hoặc sửa trực tiếp.'
            : selected?.value ? 'Đang dùng địa chỉ từ sổ địa chỉ của bạn.' : 'Vui lòng nhập địa chỉ giao hàng cho đơn này.';
        const savedNote = selected?.dataset.note || '';
        if (note && (!note.value.trim() || note.value === lastSavedNote)) note.value = savedNote;
        lastSavedNote = savedNote;
    }
    savedAddresses.addEventListener('change', event => {
        address.value = event.target.value;
        invalidate(true); lastSearch = ''; savedAddressHint(); summary();
        if (address.value.trim()) search();
        else address.focus();
    });
    address.addEventListener('input', () => {
        invalidate(true); lastSearch = ''; summary();
        const saved = Array.from(savedAddresses.options).find(option => option.value === address.value.trim());
        savedAddresses.value = saved?.value || '';
        byId('addressHint').textContent = saved?.dataset.default === 'true' ? 'Đang dùng địa chỉ mặc định.'
            : saved?.value ? 'Đang dùng địa chỉ đã lưu.' : 'Địa chỉ tùy chỉnh cho đơn hàng này.';
        status.textContent = 'Đang chờ xác định địa chỉ nhận hàng.';
        if (address.value.trim().length >= 3) lookupTimer = setTimeout(search, 1200);
    });
    address.addEventListener('change', () => { if (address.value.trim() !== lastSearch) search(); });
    type.addEventListener('change', () => {
        invalidate(false);
        byId('addressGroup').hidden = type.value === 'PICKUP';
        address.required = type.value === 'DELIVERY';
        if (type.value === 'DELIVERY') {
            requestAnimationFrame(() => map?.invalidateSize());
            status.textContent = marker && lat.value && lon.value
                ? 'Kiểm tra ghim rồi xác nhận lại điểm nhận.' : 'Tìm địa chỉ hoặc chọn điểm nhận trên bản đồ.';
        }
        summary();
        if (type.value === 'DELIVERY' && !marker && address.value.trim()) search();
    });
    locate.hidden = !window.isSecureContext || !navigator.geolocation || !map;
    locate.addEventListener('click', () => {
        invalidate(true);
        const current = revision;
        locating = true; summary(); status.textContent = 'Đang lấy vị trí hiện tại...';
        navigator.geolocation.getCurrentPosition(
            result => {
                if (current !== revision || type.value !== 'DELIVERY') return;
                const point = L.latLng(result.coords.latitude, result.coords.longitude);
                selectPoint(point); map.setView(point,16);
            },
            () => {
                if (current !== revision || type.value !== 'DELIVERY') return;
                locating = false; summary();
                status.textContent = 'Không lấy được vị trí hiện tại. Bạn có thể tìm địa chỉ hoặc chọn điểm trên bản đồ.';
            },
            {enableHighAccuracy:true, timeout:10000, maximumAge:0}
        );
    });
    form.addEventListener('submit', event => {
        if (empty || (type.value === 'DELIVERY' && !confirmed)) { event.preventDefault(); summary(); return; }
        place.disabled = true; place.textContent = 'Đang đặt hàng...';
    });
    summary();
    if (!address.value.trim()) {
        const firstSaved = Array.from(savedAddresses.options).find(option => option.value);
        if (firstSaved) { savedAddresses.value = firstSaved.value; address.value = firstSaved.value; }
    }
    if (savedAddresses.value) savedAddressHint();
    if (address.value.trim()) search();
});
