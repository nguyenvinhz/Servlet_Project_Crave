document.getElementById('fulfillmentType').addEventListener('change', function(e) {
    const addressGroup = document.getElementById('addressGroup');
    const addressInput = document.getElementById('deliveryAddress');
    if (e.target.value === 'PICKUP') {
        addressGroup.style.display = 'none';
        addressInput.removeAttribute('required');
    } else {
        addressGroup.style.display = 'block';
        addressInput.setAttribute('required', 'required');
    }
});

document.addEventListener('DOMContentLoaded', async () => {
    try {
        // Giả lập gọi API giỏ hàng của Trí
        const res = await fetch('/crave/api/cart');
        if (res.ok) {
            const data = await res.json();
            if (data.success && data.data) {
                const cart = data.data;
                document.getElementById('summarySubtotal').textContent = cart.subtotal.toLocaleString('vi-VN') + ' đ';
                const fee = 15000;
                document.getElementById('summaryFee').textContent = fee.toLocaleString('vi-VN') + ' đ';
                document.getElementById('summaryTotal').textContent = (cart.subtotal + fee).toLocaleString('vi-VN') + ' đ';
            }
        } else {
            document.getElementById('summarySubtotal').textContent = 'Chưa có dữ liệu giỏ hàng';
            document.getElementById('summaryTotal').textContent = '...';
        }
    } catch (err) {
        document.getElementById('summarySubtotal').textContent = 'Lỗi tải giỏ hàng';
    }
});

document.getElementById('checkoutForm').addEventListener('submit', async function(e) {
    e.preventDefault();
    const alertBox = document.getElementById('checkoutAlert');
    alertBox.style.display = 'none';
    alertBox.className = 'alert';

    const payload = {
        fulfillmentType: document.getElementById('fulfillmentType').value,
        receiverName: document.getElementById('receiverName').value,
        receiverPhone: document.getElementById('receiverPhone').value,
        deliveryAddress: document.getElementById('fulfillmentType').value === 'DELIVERY' 
                            ? document.getElementById('deliveryAddress').value : null,
        paymentMethod: document.getElementById('paymentMethod').value,
        customerNote: document.getElementById('customerNote').value
    };

    try {
        const res = await fetch('/crave/api/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        
        const data = await res.json();
        
        if (res.ok && data.success) {
            alertBox.textContent = 'Đặt hàng thành công! Mã đơn: ' + data.data.orderId;
            alertBox.classList.add('success');
            alertBox.style.display = 'block';
            setTimeout(() => {
                window.location.href = '/crave/orders/' + data.data.orderId;
            }, 2000);
        } else {
            alertBox.textContent = data.message || 'Có lỗi xảy ra.';
            alertBox.classList.add('error');
            alertBox.style.display = 'block';
        }
    } catch (err) {
        alertBox.textContent = 'Lỗi kết nối máy chủ.';
        alertBox.classList.add('error');
        alertBox.style.display = 'block';
    }
});
