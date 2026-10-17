<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="pageTitle" value="Thanh toán" />
<c:set var="pageStyle" value="order" />
<c:set var="pageBodyClass" value="order-page" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/assets/vendor/leaflet/leaflet.css'/>">
<main class="page-container checkout-page" id="main-content">
    <header class="customer-page-heading">
        <p class="eyebrow">Hoàn tất đặt món</p>
        <h1>Thanh toán</h1>
        <p>Kiểm tra thông tin nhận món và tổng tiền trước khi đặt hàng.</p>
    </header>
    <div class="checkout-grid">
        <section class="card checkout-form-card" aria-labelledby="delivery-title">
            <h2 id="delivery-title">Thông tin nhận món</h2>
            <div id="checkoutAlert" class="alert ${not empty sessionScope.orderError || not empty checkoutWarning ? 'error' : ''}" style="display:${not empty sessionScope.orderError || not empty checkoutWarning ? 'block' : 'none'}" role="alert" aria-live="polite"><c:out value="${sessionScope.orderError}"/><c:out value="${checkoutWarning}"/></div>
            <c:remove var="orderError" scope="session"/>
            <p><a href="<c:url value='/cart'/>">← Quay lại giỏ hàng</a></p>
            <button type="button" id="retryCheckoutBtn" class="button outline" hidden>Thử tải lại</button>
            <form id="checkoutForm" action="<c:url value='/orders/create'/>" method="post"
                  data-subtotal="${subtotal}" data-discount="${discount}" data-empty="${empty cart.items}"
                  data-store-latitude="${store.latitude()}" data-store-longitude="${store.longitude()}" data-configured="${deliveryConfigured}">
                <input type="hidden" name="deliveryLatitude" id="deliveryLatitude">
                <input type="hidden" name="deliveryLongitude" id="deliveryLongitude">
                <div class="form-group"><label for="fulfillmentType">Hình thức nhận món</label><select name="fulfillmentType" id="fulfillmentType"><option value="DELIVERY">Giao hàng tận nơi</option><option value="PICKUP">Lấy tại quán</option></select></div>
                <div class="form-group"><label for="receiverName">Người nhận</label><input type="text" name="receiverName" id="receiverName" autocomplete="name" maxlength="100" value="<c:out value='${sessionScope.currentUser.fullName}'/>" placeholder="Họ và tên người nhận" required></div>
                <div class="form-group"><label for="receiverPhone">Số điện thoại</label><input type="tel" name="receiverPhone" id="receiverPhone" autocomplete="tel" inputmode="tel" maxlength="15" value="<c:out value='${sessionScope.currentUser.phone}'/>" placeholder="0901234567" required></div>
                <div class="form-group" id="addressGroup">
                    <div class="address-label-row">
                        <label for="deliveryAddress">Địa chỉ nhận hàng</label>
                        <a href="<c:url value='/customer/addresses'/>" target="_blank" class="address-manage-link" title="Mở sổ địa chỉ để thêm hoặc chỉnh sửa">Quản lý sổ địa chỉ ↗</a>
                    </div>
                    <div id="savedAddressWrap" class="saved-address-wrap" style="display: ${empty addresses ? 'none' : 'block'}; margin-bottom: 0.65rem;">
                        <label for="savedAddressSelect" class="sub-label">Chọn từ sổ địa chỉ đã lưu</label>
                        <select id="savedAddressSelect" class="saved-address-select" aria-label="Chọn từ sổ địa chỉ đã lưu">
                            <c:forEach var="address" items="${addresses}">
                                <option value="<c:out value='${address.addressLine}'/>" data-note="<c:out value='${address.note}'/>" data-default="${address.isDefault}" ${address.isDefault ? 'selected' : ''}>${address.isDefault ? '★ [Mặc định] ' : '📍 '}<c:out value="${address.addressLine}"/><c:if test="${not empty address.note}"> (<c:out value="${address.note}"/>)</c:if></option>
                            </c:forEach>
                            <option value="" ${empty defaultAddress ? 'selected' : ''}>✏️ Nhập địa chỉ khác...</option>
                        </select>
                    </div>
                    <input type="text" name="deliveryAddress" id="deliveryAddress" value="<c:out value='${defaultAddress}'/>" autocomplete="street-address" maxlength="255" placeholder="Địa chỉ hoặc tọa độ: 10.852791, 106.772558" required>
                    <p class="field-hint" id="addressHint">Hệ thống tự động điền địa chỉ mặc định. Bạn có thể chọn địa chỉ khác hoặc sửa trực tiếp.</p>
                    <div class="delivery-location">
                        <p id="storeAddress" class="field-hint">Cửa hàng: <c:out value="${store.address()}"/></p>
                        <p class="field-hint">Nhập tên đường, địa điểm hoặc tọa độ theo thứ tự vĩ độ, kinh độ. Bạn cũng có thể chạm bản đồ khi chưa nhập địa chỉ; ô địa chỉ sẽ cập nhật theo ghim. Kéo ghim đến đúng nơi nhận rồi xác nhận.</p>
                        <div id="deliverySearchResults" class="delivery-search-results" aria-label="Kết quả tìm địa chỉ" aria-live="polite"></div>
                        <p id="matchedDeliveryAddress" class="field-hint" aria-live="polite"></p>
                        <div id="deliveryMap" class="delivery-map" aria-label="Bản đồ chọn điểm nhận hàng"></div>
                        <div class="delivery-map-actions">
                            <button type="button" id="findDeliveryAddressBtn" class="button outline" disabled>Tìm theo địa chỉ</button>
                            <button type="button" id="useMyLocationBtn" class="button outline" hidden>Dùng vị trí hiện tại</button>
                            <button type="button" id="confirmDeliveryLocationBtn" class="button" hidden>Xác nhận điểm nhận</button>
                        </div>
                        <p id="deliveryLocationStatus" class="field-hint" role="status" aria-live="polite">Chọn điểm nhận hàng để tính phí giao hàng.</p>
                        <button type="button" id="retryDeliveryQuoteBtn" class="button outline" hidden>Thử tính phí lại</button>
                    </div>
                </div>
                <div class="form-group"><label for="paymentMethod">Phương thức thanh toán</label><select name="paymentMethod" id="paymentMethod"><option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản ngân hàng</option><option value="E_WALLET">Ví điện tử</option></select></div>
                <div class="form-group"><label for="customerNote">Ghi chú <span class="optional-label">(không bắt buộc)</span></label><textarea name="customerNote" id="customerNote" rows="3" maxlength="500" placeholder="Ví dụ: gọi trước khi giao"></textarea></div>
                <button type="submit" id="placeOrderBtn" class="btn-primary" disabled>Đặt hàng</button>
            </form>
        </section>
        <aside class="order-summary" aria-labelledby="summary-title">
            <h2 id="summary-title">Tóm tắt đơn hàng</h2>
            <div class="summary-row"><span>Tạm tính</span><strong id="summarySubtotal"><fmt:formatNumber value="${subtotal}" maxFractionDigits="0"/> đ</strong></div>
            <div class="summary-row"><span>Phí giao hàng</span><strong id="summaryFee">Đang tải...</strong></div>
            <div class="summary-row discount-row" id="summaryDiscountRow" ${discount > 0 ? '' : 'hidden'}><span>Giảm giá</span><strong id="summaryDiscount"><fmt:formatNumber value="${discount}" maxFractionDigits="0"/> đ</strong></div>
            <div class="summary-row total"><span>Tổng cộng</span><strong id="summaryTotal">Đang tải...</strong></div>
            <p class="summary-hint">Miễn phí giao hàng cho đơn có tiền món trên 150.000đ trước giảm giá, áp dụng tại khu vực được hỗ trợ. Lấy tại quán miễn phí.</p>
        </aside>
    </div>
</main>
<script src="<c:url value='/assets/vendor/leaflet/leaflet.js'/>"></script>
<script src="<c:url value='/assets/js/checkout.js?v=20261017-map3'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
