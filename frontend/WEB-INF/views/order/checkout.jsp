<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Thanh toán"/><c:set var="pageStyle" value="order"/><c:set var="pageBodyClass" value="order-page"/>
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/assets/vendor/leaflet/leaflet.css'/>">
<main class="page-container checkout-page" id="main-content">
<header class="customer-page-heading"><p class="eyebrow">Hoàn tất đặt món</p><h1>Thanh toán</h1><p>Kiểm tra thông tin nhận món và tổng tiền trước khi đặt hàng.</p></header>
<c:if test="${not empty flashError}"><p class="form-error" role="alert"><c:out value="${flashError}"/></p></c:if>
<div class="checkout-grid"><section class="card checkout-form-card"><h2>Thông tin nhận món</h2><p><a href="<c:url value='/cart'/>">← Quay lại giỏ hàng</a></p>
<form id="checkoutForm" method="post" action="<c:url value='/checkout'/>">
<div class="form-group"><label for="fulfillmentType">Hình thức nhận món</label><select name="fulfillmentType" id="fulfillmentType"><option value="DELIVERY">Giao hàng tận nơi</option><option value="PICKUP" ${param.fulfillmentType == 'PICKUP' ? 'selected' : ''}>Lấy tại quán</option></select></div>
<div class="form-group"><label for="receiverName">Người nhận</label><input name="receiverName" id="receiverName" maxlength="100" autocomplete="name" value="<c:out value='${empty param.receiverName ? sessionScope.currentUser.fullName : param.receiverName}'/>" required></div>
<div class="form-group"><label for="receiverPhone">Số điện thoại</label><input name="receiverPhone" id="receiverPhone" type="tel" maxlength="15" autocomplete="tel" value="<c:out value='${empty param.receiverPhone ? sessionScope.currentUser.phone : param.receiverPhone}'/>" required></div>
<div id="addressGroup"><div class="form-group"><label for="savedAddressSelect">Sổ địa chỉ</label><select id="savedAddressSelect"><option value="">Chọn địa chỉ đã lưu</option><c:forEach var="address" items="${addresses}"><option value="<c:out value='${address.addressLine()}'/>"><c:out value="${address.addressLine()}"/>${address.isDefault() ? ' (Mặc định)' : ''}</option></c:forEach></select><a href="<c:url value='/customer/addresses'/>">Quản lý sổ địa chỉ</a></div>
<div class="form-group"><label for="deliveryAddress">Địa chỉ nhận hàng</label><input name="deliveryAddress" id="deliveryAddress" maxlength="255" value="<c:out value='${empty resolvedAddress ? param.deliveryAddress : resolvedAddress}'/>" placeholder="Nhập địa chỉ hoặc vĩ độ, kinh độ"></div>
<p class="field-hint">Cửa hàng: <c:out value="${storeAddress}"/>. Chọn kết quả tìm kiếm hoặc ghim trên bản đồ rồi tính phí.</p>
<button class="button outline" name="action" value="search" formnovalidate>Tìm địa chỉ</button>
<div id="deliverySearchResults"><c:forEach var="location" items="${locations}"><button type="button" class="button outline location-result" data-latitude="${location.latitude()}" data-longitude="${location.longitude()}" data-address="<c:out value='${location.label()}'/>"><c:out value="${location.label()}"/>${location.approximate() ? ' — kiểm tra lại số nhà trên bản đồ' : ''}</button></c:forEach></div>
<div id="deliveryMap" class="delivery-map" data-store-latitude="${storeLatitude}" data-store-longitude="${storeLongitude}" aria-label="Chọn vị trí nhận hàng"></div>
<div class="form-group"><label for="deliveryLatitude">Vĩ độ</label><input name="deliveryLatitude" id="deliveryLatitude" type="number" step="any" min="-90" max="90" value="<c:out value='${param.deliveryLatitude}'/>"><label for="deliveryLongitude">Kinh độ</label><input name="deliveryLongitude" id="deliveryLongitude" type="number" step="any" min="-180" max="180" value="<c:out value='${param.deliveryLongitude}'/>"></div>
<button class="button outline" type="button" id="useMyLocationBtn">Dùng vị trí hiện tại</button><button class="button outline" name="action" value="reverse" formnovalidate>Lấy địa chỉ tại ghim</button>
<p class="field-hint" id="deliveryLocationStatus"><c:out value="${deliveryMessage}"/></p></div>
<div class="form-group"><label for="paymentMethod">Phương thức thanh toán</label><select name="paymentMethod" id="paymentMethod"><option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER" ${param.paymentMethod == 'BANK_TRANSFER' ? 'selected' : ''}>Chuyển khoản ngân hàng</option><option value="E_WALLET" ${param.paymentMethod == 'E_WALLET' ? 'selected' : ''}>Ví điện tử</option></select></div>
<div class="form-group"><label for="customerNote">Ghi chú</label><textarea name="customerNote" id="customerNote" maxlength="500" rows="3"><c:out value="${param.customerNote}"/></textarea></div>
<button name="action" value="quote" class="button outline" formnovalidate>Tính lại tổng tiền</button>
<button name="action" value="placeOrder" id="placeOrderBtn" class="btn-primary" ${empty cart.items ? 'disabled' : ''}>Đặt hàng</button>
</form></section><aside class="order-summary"><h2>Tóm tắt đơn hàng</h2>
<c:forEach var="item" items="${cart.items}"><div class="summary-row"><span><c:out value="${item.foodName}"/> × ${item.quantity}</span><strong><fmt:formatNumber value="${item.lineTotal}"/> đ</strong></div></c:forEach>
<div class="summary-row"><span>Tạm tính</span><strong><fmt:formatNumber value="${subtotal}"/> đ</strong></div>
<div class="summary-row"><span>Giảm giá</span><strong><fmt:formatNumber value="${discount}"/> đ</strong></div>
<div class="summary-row"><span>Phí giao hàng</span><strong><c:choose><c:when test="${deliveryReady}"><fmt:formatNumber value="${fee}"/> đ</c:when><c:otherwise>Chọn vị trí để tính phí</c:otherwise></c:choose></strong></div>
<div class="summary-row total"><span>Tổng cộng${deliveryReady ? '' : ' (chưa gồm phí giao hàng)'}</span><strong><fmt:formatNumber value="${total}"/> đ</strong></div>
<p class="summary-hint">Lấy tại quán miễn phí. Giao hàng trong phạm vi hỗ trợ; miễn phí giao hàng khi tiền món trên 150.000đ trước giảm giá. Tổng tiền được kiểm tra lại khi đặt hàng.</p>
</aside></div></main>
<script src="<c:url value='/assets/vendor/leaflet/leaflet.js'/>"></script><script src="<c:url value='/assets/js/checkout.js'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>