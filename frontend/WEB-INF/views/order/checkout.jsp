<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Checkout" scope="request"/>
<%@ include file="../components/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/assets/css/order.css'/>">

<main class="page order-page order-theme">
    <div class="checkout-grid">
        <!-- Cột trái: Delivery & Payment -->
        <section class="checkout-left">
            <h1>Checkout</h1>
            <div class="card checkout-section">
                <h2>Delivery information</h2>
                
                <div class="field-grid">
                    <div>
                        <label for="receiverName">Người nhận</label>
                        <input type="text" id="receiverName" placeholder="Tên người nhận" required>
                    </div>
                    <div>
                        <label for="receiverPhone">Điện thoại</label>
                        <input type="text" id="receiverPhone" placeholder="Số điện thoại" required>
                    </div>
                </div>

                <label for="fulfillmentType">Hình thức nhận hàng</label>
                <select id="fulfillmentType">
                    <option value="DELIVERY">Giao tận nơi</option>
                    <option value="PICKUP">Đến lấy</option>
                </select>

                <label for="deliveryAddress">Địa chỉ giao hàng</label>
                <input type="text" id="deliveryAddress" placeholder="Nhập địa chỉ (nếu giao hàng)">

                <label for="customerNote">Ghi chú (tuỳ chọn)</label>
                <textarea id="customerNote" rows="2" placeholder="VD: Gọi trước khi giao"></textarea>
            </div>

            <div class="card checkout-section">
                <h2>Payment method</h2>
                <div class="radio-group" id="paymentMethodGroup">
                    <label>
                        <input type="radio" name="paymentMethod" value="CASH" checked>
                        Cash on Delivery
                    </label>
                    <label>
                        <input type="radio" name="paymentMethod" value="BANK_TRANSFER">
                        Credit / Debit Card
                    </label>
                    <label>
                        <input type="radio" name="paymentMethod" value="E_WALLET">
                        E-wallet
                    </label>
                </div>
            </div>
        </section>

        <!-- Cột phải: Summary -->
        <aside class="checkout-right">
            <div class="order-summary">
                <h2>Your order</h2>
                <div class="summary-row">
                    <span>Smoky Stack Burger</span>
                    <span>12.50</span>
                </div>
                <div class="summary-row">
                    <span>Delivery fee</span>
                    <span>2.50</span>
                </div>
                <div class="summary-row">
                    <span>Discount</span>
                    <span>-2.58</span>
                </div>
                <div class="summary-row total">
                    <span>Total</span>
                    <span>12.42</span>
                </div>
                
                <button class="btn-primary" onclick="submitOrder()">Place Order</button>
            </div>
        </aside>
    </div>
</main>

<script>
    function submitOrder() {
        const selectedPayment = document.querySelector('input[name="paymentMethod"]:checked').value;
        const data = {
            fulfillmentType: document.getElementById('fulfillmentType').value,
            receiverName: document.getElementById('receiverName').value,
            receiverPhone: document.getElementById('receiverPhone').value,
            deliveryAddress: document.getElementById('deliveryAddress').value,
            customerNote: document.getElementById('customerNote').value,
            paymentMethod: selectedPayment
        };

        fetch('${pageContext.request.contextPath}/api/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        }).then(res => res.json())
          .then(json => {
              console.log('Response:', json);
              if (json.success) {
                  // Forward sang trang tracking order sau khi đặt thành công
                  alert('Đặt hàng thành công! Mã đơn: ' + json.data.orderId);
                  window.location.href = '${pageContext.request.contextPath}/orders/' + json.data.orderId;
              } else {
                  alert('Lỗi: ' + json.error.message);
              }
          })
          .catch(err => console.error('Error:', err));
    }
</script>

<%@ include file="../components/footer.jspf" %>
