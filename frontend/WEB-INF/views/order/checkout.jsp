<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Thanh toán | Crave</title>
</head>
<body>
    <h1>Trang Thanh toán</h1>
    <p>Chức năng Checkout sẽ được phát triển trong Ngày 2.</p>
    <div id="checkout-form">
        <label>Hình thức nhận hàng:</label>
        <select id="fulfillmentType">
            <option value="DELIVERY">Giao tận nơi</option>
            <option value="PICKUP">Đến lấy</option>
        </select><br/>

        <label>Người nhận:</label>
        <input type="text" id="receiverName" /><br/>

        <label>Điện thoại:</label>
        <input type="text" id="receiverPhone" /><br/>

        <label>Địa chỉ giao hàng:</label>
        <input type="text" id="deliveryAddress" /><br/>

        <label>Ghi chú:</label>
        <input type="text" id="customerNote" /><br/>

        <label>Phương thức thanh toán:</label>
        <select id="paymentMethod">
            <option value="CASH">Tiền mặt</option>
            <option value="BANK_TRANSFER">Chuyển khoản</option>
            <option value="E_WALLET">Ví điện tử</option>
        </select><br/>

        <button onclick="submitOrder()">Xác nhận đặt hàng (JSON)</button>
    </div>

    <script>
        function submitOrder() {
            const data = {
                fulfillmentType: document.getElementById('fulfillmentType').value,
                receiverName: document.getElementById('receiverName').value,
                receiverPhone: document.getElementById('receiverPhone').value,
                deliveryAddress: document.getElementById('deliveryAddress').value,
                customerNote: document.getElementById('customerNote').value,
                paymentMethod: document.getElementById('paymentMethod').value
            };

            fetch('${pageContext.request.contextPath}/api/orders', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(data)
            }).then(res => res.json())
              .then(json => console.log('Response:', json))
              .catch(err => console.error('Error:', err));
        }
    </script>
</body>
</html>
