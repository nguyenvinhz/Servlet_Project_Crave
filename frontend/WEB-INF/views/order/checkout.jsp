<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Thanh toán" />
<c:set var="pageStyle" value="order" />
<c:set var="pageBodyClass" value="order-page" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<main class="page-container checkout-page" id="main-content">
    <header class="customer-page-heading">
        <p class="eyebrow">Hoàn tất đặt món</p>
        <h1>Thanh toán</h1>
        <p>Kiểm tra thông tin nhận món và tổng tiền trước khi đặt hàng.</p>
    </header>
    <div class="checkout-grid">
        <section class="card checkout-form-card" aria-labelledby="delivery-title">
            <h2 id="delivery-title">Thông tin nhận món</h2>
            <div id="checkoutAlert" class="alert" role="alert" aria-live="polite"></div>
            <p><a href="<c:url value='/cart'/>">← Quay lại giỏ hàng</a></p>
            <button type="button" id="retryCheckoutBtn" class="button outline" hidden>Thử tải lại</button>
            <form id="checkoutForm">
                <div class="form-group"><label for="fulfillmentType">Hình thức nhận món</label><select name="fulfillmentType" id="fulfillmentType"><option value="DELIVERY">Giao hàng tận nơi</option><option value="PICKUP">Lấy tại quán</option></select></div>
                <div class="form-group"><label for="receiverName">Người nhận</label><input type="text" name="receiverName" id="receiverName" autocomplete="name" maxlength="100" placeholder="Họ và tên người nhận" required></div>
                <div class="form-group"><label for="receiverPhone">Số điện thoại</label><input type="tel" name="receiverPhone" id="receiverPhone" autocomplete="tel" inputmode="tel" maxlength="15" placeholder="0901234567" required></div>
                <div class="form-group" id="addressGroup"><label for="deliveryAddress">Địa chỉ nhận hàng</label><input type="text" name="deliveryAddress" id="deliveryAddress" autocomplete="street-address" maxlength="255" placeholder="Số nhà, đường, phường/xã, tỉnh/thành" required></div>
                <div class="form-group"><label for="paymentMethod">Phương thức thanh toán</label><select name="paymentMethod" id="paymentMethod"><option value="CASH">Tiền mặt khi nhận món</option><option value="BANK_TRANSFER">Chuyển khoản qua PayOS (QR ngân hàng)</option><option value="E_WALLET">Ví điện tử · cửa hàng xác nhận</option></select><p class="summary-hint" id="paymentHint" hidden>Bạn sẽ được chuyển đến PayOS để quét mã QR. Đơn hàng được xác nhận thanh toán khi ngân hàng ghi nhận giao dịch.</p></div>
                <div class="form-group"><label for="customerNote">Ghi chú <span class="optional-label">(không bắt buộc)</span></label><textarea name="customerNote" id="customerNote" rows="3" maxlength="500" placeholder="Ví dụ: gọi trước khi giao"></textarea></div>
                <button type="submit" id="placeOrderBtn" class="btn-primary" disabled>Đặt hàng</button>
            </form>
        </section>
        <aside class="order-summary" aria-labelledby="summary-title">
            <h2 id="summary-title">Tóm tắt đơn hàng</h2>
            <div class="summary-row"><span>Tạm tính</span><strong id="summarySubtotal">Đang tải...</strong></div>
            <div class="summary-row"><span>Phí giao hàng</span><strong id="summaryFee">Đang tải...</strong></div>
            <div class="summary-row discount-row" id="summaryDiscountRow" hidden><span>Giảm giá</span><strong id="summaryDiscount">0 đ</strong></div>
            <div class="summary-row total"><span>Tổng cộng</span><strong id="summaryTotal">Đang tải...</strong></div>
            <p class="summary-hint">Phí giao hàng được cập nhật theo hình thức nhận món. Mã ưu đãi được kiểm tra lại trước khi đặt đơn.</p>
        </aside>
    </div>
</main>
<script src="<c:url value='/assets/js/checkout.js'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
