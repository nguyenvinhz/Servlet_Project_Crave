'use strict';
(() => {
    const form = document.getElementById('checkoutForm');
    if (!form) return;
    const address = document.getElementById('deliveryAddress');
    const latitude = document.getElementById('deliveryLatitude');
    const longitude = document.getElementById('deliveryLongitude');
    const type = document.getElementById('fulfillmentType');
    const status = document.getElementById('deliveryLocationStatus');
    const saved = document.getElementById('savedAddressSelect');
    let map, marker;
    let coordinateAddress = '';
    function point(lat, lon) {
        const oldCoordinateText = latitude.value && longitude.value
            ? `${Number(latitude.value).toFixed(6)}, ${Number(longitude.value).toFixed(6)}` : '';
        const replaceAddress = !address.value.trim() || address.value.trim() === coordinateAddress
            || address.value.trim() === oldCoordinateText;
        latitude.value = lat; longitude.value = lon;
        if (replaceAddress) {
            coordinateAddress = `${Number(lat).toFixed(6)}, ${Number(lon).toFixed(6)}`;
            address.value = coordinateAddress;
        }
        if (map) {
            if (marker) marker.setLatLng([lat, lon]);
            else marker = L.marker([lat, lon], {draggable: true}).addTo(map).on('dragend', () => {
                const value = marker.getLatLng(); point(value.lat, value.lng);
            });
            map.setView([lat, lon], 16);
        }
        status.textContent = 'Đã chọn điểm nhận. Bấm Tính lại tổng tiền để kiểm tra phí.';
    }
    function resetPoint() { latitude.value = ''; longitude.value = ''; coordinateAddress = ''; status.textContent = 'Chọn và xác nhận lại vị trí nhận hàng.'; }
    address.addEventListener('input', resetPoint);
    saved.addEventListener('change', () => { address.value = saved.value; resetPoint(); });
    document.querySelectorAll('.location-result').forEach(button => button.addEventListener('click', () => {
        address.value = button.dataset.address; point(Number(button.dataset.latitude), Number(button.dataset.longitude));
    }));
    function fulfillment() {
        const pickup = type.value === 'PICKUP';
        document.getElementById('addressGroup').hidden = pickup;
        address.required = !pickup;
        if (map && !pickup) setTimeout(() => map.invalidateSize(), 0);
    }
    type.addEventListener('change', fulfillment);
    const container = document.getElementById('deliveryMap');
    if (window.L && container && container.dataset.storeLatitude && container.dataset.storeLongitude) {
        const store = [Number(container.dataset.storeLatitude), Number(container.dataset.storeLongitude)];
        map = L.map(container).setView(store, 14);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {attribution: '&copy; OpenStreetMap contributors'}).addTo(map);
        L.marker(store).addTo(map).bindPopup('Cửa hàng Crave');
        map.on('click', event => point(event.latlng.lat, event.latlng.lng));
        if (latitude.value && longitude.value) point(Number(latitude.value), Number(longitude.value));
    }
    document.getElementById('useMyLocationBtn').addEventListener('click', () => {
        if (!navigator.geolocation) { status.textContent = 'Trình duyệt không hỗ trợ vị trí hiện tại.'; return; }
        navigator.geolocation.getCurrentPosition(position => point(position.coords.latitude, position.coords.longitude),
            () => { status.textContent = 'Không lấy được vị trí. Hãy chọn ghim trên bản đồ.'; },
            {enableHighAccuracy:true, maximumAge:0, timeout:10000});
    });
    fulfillment();
})();
