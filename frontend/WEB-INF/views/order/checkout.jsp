<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Thanh toán | Crave</title>
    <link rel="stylesheet" href="/crave/assets/css/styles.css">
    <style>
        .checkout-container { max-width: 800px; margin: 40px auto; display: flex; gap: 20px; }
        .form-section { flex: 2; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
        .summary-section { flex: 1; background: #fafafa; padding: 20px; border-radius: 8px; border: 1px solid #ddd; }
        .form-group { margin-bottom: 15px; }
        .form-group label { display: block; margin-bottom: 5px; font-weight: bold; }
        .form-group input, .form-group select, .form-group textarea { width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; }
        .btn-submit { background: var(--primary-color, #e74c3c); color: white; border: none; padding: 12px 20px; border-radius: 4px; cursor: pointer; width: 100%; font-size: 16px; font-weight: bold; }
        .btn-submit:hover { background: #c0392b; }
        .alert { padding: 10px; margin-bottom: 15px; display: none; border-radius: 4px; }
        .alert.error { background: #fee; color: #c0392b; border: 1px solid #fcc; }
        .alert.success { background: #efe; color: #27ae60; border: 1px solid #cfc; }
    </style>
</head>
<body>
    <div class="checkout-container">
        <div class="form-section">
            <h2>Thông tin giao hàng</h2>
            <div id="checkoutAlert" class="alert"></div>
            <form id="checkoutForm">
                <div class="form-group">
                    <label>Hình thức nhận hàng</label>
                    <select name="fulfillmentType" id="fulfillmentType">
                        <option value="DELIVERY">Giao hàng tận nơi</option>
                        <option value="PICKUP">Lấy tại quán</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Người nhận</label>
                    <input type="text" name="receiverName" id="receiverName" required />
                </div>
                <div class="form-group">
                    <label>Số điện thoại</label>
                    <input type="text" name="receiverPhone" id="receiverPhone" required />
                </div>
                <div class="form-group" id="addressGroup">
                    <label>Địa chỉ nhận hàng</label>
                    <input type="text" name="deliveryAddress" id="deliveryAddress" required />
                </div>
                <div class="form-group">
                    <label>Phương thức thanh toán</label>
                    <select name="paymentMethod" id="paymentMethod">
                        <option value="CASH">Tiền mặt</option>
                        <option value="BANK_TRANSFER">Chuyển khoản ngân hàng</option>
                        <option value="E_WALLET">Ví điện tử (Momo, ZaloPay...)</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Ghi chú (Không bắt buộc)</label>
                    <textarea name="customerNote" id="customerNote" rows="3"></textarea>
                </div>
                <button type="submit" class="btn-submit">Đặt hàng</button>
            </form>
        </div>
        <div class="summary-section">
            <h3>Tóm tắt đơn hàng</h3>
            <p><strong>Tạm tính:</strong> <span id="summarySubtotal">Đang tải...</span></p>
            <p><strong>Phí giao hàng:</strong> <span id="summaryFee">15,000 đ</span></p>
            <hr>
            <h4>Tổng cộng: <span id="summaryTotal">...</span></h4>
        </div>
    </div>

    <script>
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
                        window.location.href = '/crave/orders';
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
    </script>
</body>
</html>
