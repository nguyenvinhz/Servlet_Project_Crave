<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Chi tiết đơn hàng" />
<c:set var="pageStyle" value="order" />
<c:set var="pageBodyClass" value="order-page" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<main class="page-container detail-container" id="main-content">
    <a href="<c:url value='/orders'/>" class="btn-back">← Quay lại đơn hàng của bạn</a>
    <div id="orderContent" aria-live="polite"><div class="card order-loading"><p>Đang tải chi tiết đơn hàng...</p></div></div>
    <template id="payosPaymentTemplate">
        <section class="card payos-payment" id="payosPayment" aria-labelledby="payosTitle">
            <h2 id="payosTitle">Thanh toán bằng mã QR</h2>
            <p id="payosHint" class="payos-hint">Quét mã bằng ứng dụng ngân hàng hoặc ví hỗ trợ VietQR. Kiểm tra số tiền và nội dung trước khi xác nhận.</p>
            <p id="payosStatus" class="payos-status" role="status" aria-live="polite">Đang tạo mã thanh toán...</p>
            <div id="payosDetails" class="payos-details" hidden>
                <div class="payos-qr-wrap">
                    <img id="payosQr" class="payos-qr" width="240" height="240" alt="Mã QR chuyển khoản cho đơn hàng" hidden>
                    <p id="payosQrError" class="payos-hint" hidden></p>
                </div>
                <dl class="payos-bank-details">
                    <div><dt>Số tiền</dt><dd id="payosAmount" class="payos-amount"></dd></div>
                    <div><dt>Số tài khoản</dt><dd id="payosAccountNumber"></dd></div>
                    <div><dt>Chủ tài khoản</dt><dd id="payosAccountName"></dd></div>
                    <div><dt>Nội dung chuyển khoản</dt><dd id="payosDescription"></dd></div>
                </dl>
            </div>
            <div id="payosActions" class="payos-actions">
                <a id="payosCheckoutLink" class="button primary" target="_blank" rel="noopener noreferrer" hidden>Mở trang thanh toán</a>
                <button id="payosRetry" class="button outline" type="button" hidden>Thử lại</button>
            </div>
        </section>
    </template>
</main>
<script src="<c:url value='/assets/js/order-detail.js'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
