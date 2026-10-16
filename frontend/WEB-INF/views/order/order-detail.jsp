<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Chi tiết đơn hàng"/>
<c:set var="pageStyle" value="order"/>
<c:set var="pageBodyClass" value="order-page"/>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container order-detail-page" id="main-content"
      data-payment-pending="${not empty paymentLink and (paymentLink.status() == 'PENDING' or paymentLink.status() == 'PROCESSING') and order.payment.status != 'SUCCESS' and order.payment.status != 'REFUNDED' and order.status != 'CANCELLED'}">
    <header class="customer-page-heading">
        <a href="<c:url value='/orders'/>">← Đơn hàng của bạn</a>
        <h1>Đơn <c:out value="${order.orderId}"/></h1>
        <p>Trạng thái: <strong><c:out value="${order.status}"/></strong></p>
    </header>
    <c:if test="${not empty flashError}">
        <p class="form-error" role="alert"><c:out value="${flashError}"/></p>
    </c:if>
    <c:if test="${not empty flashMessage}">
        <p role="status"><c:out value="${flashMessage}"/></p>
    </c:if>
    <div class="checkout-grid">
        <section class="card">
            <h2>Thông tin nhận món</h2>
            <p><c:out value="${order.receiverName}"/> · <c:out value="${order.receiverPhone}"/></p>
            <p><c:out value="${order.fulfillmentType}"/> · <c:out value="${order.deliveryAddress}"/></p>
            <p><c:out value="${order.customerNote}"/></p>

            <h2>Món đã đặt</h2>
            <c:forEach var="item" items="${order.items}">
                <div class="summary-row">
                    <div>
                        <strong><c:out value="${item.foodNameSnapshot}"/> × ${item.quantity}</strong>
                        <c:forEach var="option" items="${item.options}">
                            <p><c:out value="${option.optionName}"/> (+<fmt:formatNumber value="${option.extraPrice}"/> đ)</p>
                        </c:forEach>
                        <p><c:out value="${item.note}"/></p>
                    </div>
                    <strong><fmt:formatNumber value="${item.lineTotal}"/> đ</strong>
                </div>
            </c:forEach>

            <h2>Lịch sử trạng thái</h2>
            <ol>
                <c:forEach var="entry" items="${order.history}">
                    <li><c:out value="${entry.changedAt}"/> · <c:out value="${entry.status}"/> · <c:out value="${entry.note}"/></li>
                </c:forEach>
            </ol>
            <c:if test="${order.status == 'PENDING_CONFIRMATION'}">
                <form method="post" action="<c:url value='/orders/${order.orderId}/cancel'/>">
                    <label for="cancelReason">Lý do hủy</label>
                    <input id="cancelReason" name="reason" maxlength="500">
                    <button class="button outline">Hủy đơn hàng</button>
                </form>
            </c:if>
        </section>
        <aside class="order-summary">
            <h2>Thanh toán</h2>
            <div class="summary-row"><span>Tạm tính</span><strong><fmt:formatNumber value="${order.subtotal}"/> đ</strong></div>
            <div class="summary-row"><span>Giảm giá</span><strong><fmt:formatNumber value="${order.discountAmount}"/> đ</strong></div>
            <div class="summary-row"><span>Phí giao hàng</span><strong><fmt:formatNumber value="${order.deliveryFee}"/> đ</strong></div>
            <div class="summary-row total"><span>Tổng cộng</span><strong><fmt:formatNumber value="${order.totalAmount}"/> đ</strong></div>
            <p><c:out value="${order.payment.method}"/> · <c:out value="${order.payment.status}"/></p>
            <c:if test="${order.payment.method != 'CASH' and order.payment.status != 'SUCCESS' and order.payment.status != 'REFUNDED' and order.status != 'CANCELLED'}">
                <c:if test="${not empty paymentMessage}">
                    <p role="status"><c:out value="${paymentMessage}"/></p>
                </c:if>
                <c:choose>
                    <c:when test="${not empty paymentLink and not empty paymentLink.checkoutUrl()}">
                        <p>Quét QR bằng ứng dụng ngân hàng hoặc ví hỗ trợ VietQR.</p>
                        <img alt="Mã QR thanh toán" src="<c:url value='/payments/payos/${order.orderId}/qr'/>" width="280" height="280">
                        <p><c:out value="${paymentLink.accountName()}"/> · <c:out value="${paymentLink.accountNumber()}"/></p>
                        <p>Nội dung: <c:out value="${paymentLink.description()}"/></p>
                        <a class="button primary" href="<c:out value='${paymentLink.checkoutUrl()}'/>" target="_blank" rel="noopener noreferrer">Mở trang thanh toán payOS</a>
                    </c:when>
                    <c:otherwise>
                        <form method="post" action="<c:url value='/payments/payos/${order.orderId}'/>">
                            <button class="button primary">Tạo mã QR thanh toán</button>
                        </form>
                    </c:otherwise>
                </c:choose>
                <a class="button outline" href="<c:url value='/orders/${order.orderId}'/>">Kiểm tra thanh toán</a>
            </c:if>
        </aside>
    </div>
</main>

<script src="<c:url value='/assets/js/order-detail.js'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>