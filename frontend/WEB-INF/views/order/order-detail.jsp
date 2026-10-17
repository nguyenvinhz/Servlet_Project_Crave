<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="labels.jspf" %>
<c:set var="pageTitle" value="Chi tiết đơn hàng" />
<c:set var="pageStyle" value="order" />
<c:set var="pageBodyClass" value="order-page" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<main class="page-container detail-container" id="main-content">
    <a href="<c:url value='/orders'/>" class="btn-back">← Quay lại đơn hàng của bạn</a>
    <c:if test="${not empty sessionScope.orderError}"><div class="alert error" style="display:block" role="alert"><c:out value="${sessionScope.orderError}"/></div><c:remove var="orderError" scope="session"/></c:if>
    <c:if test="${not empty sessionScope.orderMessage}"><div class="alert success" style="display:block" role="status"><c:out value="${sessionScope.orderMessage}"/></div><c:remove var="orderMessage" scope="session"/></c:if>
    <div id="orderContent" aria-live="polite">
        <div class="tracking-header card" style="padding:1.75rem;margin-bottom:1.5rem">
            <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:1rem">
                <h1>Đơn hàng #<c:out value="${order.orderId}"/></h1>
                <div style="display:flex;align-items:center;gap:0.75rem;flex-wrap:wrap">
                    <span class="order-status-badge ${orderBadges[order.status.toString()]}" style="font-size:0.9rem;padding:0.4rem 1rem"><c:out value="${orderLabels[order.status.toString()]}"/></span>
                    <c:if test="${order.status == 'PENDING_CONFIRMATION' || order.status == 'PREPARING'}"><button type="button" class="btn-cancel-order" data-cancel-order="<c:out value='${order.orderId}'/>" title="Hủy đơn hàng này">✕ Hủy đơn</button></c:if>
                </div>
            </div>
            <p>Thời gian đặt: <time data-date="<c:out value='${order.orderedAt}'/>"><c:out value="${order.orderedAt}"/></time></p>
        </div>
        <div class="card" style="padding:1.5rem;margin-bottom:1.5rem">
            <c:choose><c:when test="${order.status == 'CANCELLED'}">
                <div style="background:#fff1ee;border:1px solid #f0c5bc;padding:1.25rem;border-radius:10px;text-align:center;color:var(--error,#b42318)"><span style="font-size:1.5rem;display:block;margin-bottom:0.25rem">✕</span><strong>Đơn hàng đã bị hủy</strong><p style="margin:0.25rem 0 0;font-size:0.85rem;color:var(--text-muted)">Đơn hàng đã được ghi nhận hủy trong hệ thống.</p>
                    <c:forEach var="entry" items="${order.history}"><c:if test="${entry.status == 'CANCELLED'}"><p style="margin-top:0.75rem"><c:out value="${entry.note}"/></p></c:if></c:forEach>
                </div>
            </c:when><c:otherwise>
                <div class="stepper" data-order-status="${order.status}" data-fulfillment="${order.fulfillmentType}">
                    <c:forEach var="step" items="${['PENDING_CONFIRMATION','PREPARING','DELIVERING','COMPLETED']}" varStatus="index">
                        <c:if test="${order.fulfillmentType != 'PICKUP' || step != 'DELIVERING'}"><div class="step" data-step="${step}"><div class="step-icon">${index.count}</div><div class="step-label"><c:out value="${orderLabels[step]}"/></div></div></c:if>
                    </c:forEach>
                </div>
            </c:otherwise></c:choose>
        </div>
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(min(280px,100%),1fr));gap:1.5rem;margin-bottom:1.5rem">
            <div class="card" style="padding:1.5rem">
                <h3 style="margin-top:0;margin-bottom:1rem;font-size:1.1rem;border-bottom:1px solid var(--border);padding-bottom:0.5rem">📍 Thông tin giao nhận</h3>
                <p style="margin:0.4rem 0"><strong>Người nhận:</strong> <c:out value="${order.receiverName}"/></p>
                <p style="margin:0.4rem 0"><strong>Số điện thoại:</strong> <c:out value="${order.receiverPhone}"/></p>
                <p style="margin:0.4rem 0"><strong>Hình thức:</strong> ${order.fulfillmentType == 'DELIVERY' ? 'Giao hàng tận nơi' : 'Lấy tại quán'}</p>
                <p style="margin:0.4rem 0"><strong>Địa chỉ:</strong> <c:out value="${empty order.deliveryAddress ? 'Nhận tại quầy nhà hàng' : order.deliveryAddress}"/></p>
                <p style="margin:0.4rem 0;color:var(--text-muted);font-size:0.9rem"><strong>Ghi chú:</strong> <c:out value="${empty order.customerNote ? 'Không có ghi chú' : order.customerNote}"/></p>
            </div>
            <div class="card" style="padding:1.5rem">
                <h3 style="margin-top:0;margin-bottom:1rem;font-size:1.1rem;border-bottom:1px solid var(--border);padding-bottom:0.5rem">💳 Thông tin thanh toán</h3>
                <p style="margin:0.4rem 0"><strong>Phương thức:</strong> <c:out value="${methodLabels[order.payment.method]}"/></p>
                <p style="margin:0.4rem 0"><strong>Trạng thái:</strong> <span data-payment-status style="font-weight:700"><c:out value="${paymentLabels[order.payment.status]}"/></span></p>
                <p style="margin:0.4rem 0;font-size:0.85rem;color:var(--text-muted)">Mã thanh toán: #<c:out value="${order.payment.paymentId}"/></p>
            </div>
        </div>
        <c:if test="${onlinePayment && order.status != 'CANCELLED' && order.payment.status != 'SUCCESS' && order.payment.status != 'REFUNDED'}">
            <%@ include file="payos-payment.jspf" %>
        </c:if>
        <div class="card" style="padding:1.5rem;margin-bottom:1.5rem">
            <h3 style="margin-top:0;margin-bottom:1rem;font-size:1.1rem;border-bottom:1px solid var(--border);padding-bottom:0.5rem">🍲 Danh sách món đã đặt</h3>
            <c:forEach var="item" items="${order.items}">
                <div style="display:flex;justify-content:space-between;align-items:flex-start;padding:0.75rem 0;border-bottom:1px dashed var(--border)">
                    <div><div style="font-weight:700;font-size:1rem">${item.quantity}x <c:out value="${item.foodName}"/></div>
                        <c:if test="${not empty item.options}"><div style="font-size:0.85rem;color:var(--text-muted);margin-top:0.2rem"><c:forEach var="option" items="${item.options}" varStatus="index">+ <c:out value="${option.optionName}"/> (<fmt:formatNumber value="${option.extraPrice}" maxFractionDigits="0"/> đ)${index.last ? '' : ', '}</c:forEach></div></c:if>
                        <c:if test="${not empty item.note}"><div style="font-size:0.85rem;color:var(--accent-text,#a63a1b);font-style:italic;margin-top:0.2rem">Ghi chú: <c:out value="${item.note}"/></div></c:if>
                    </div>
                    <div style="font-weight:700;font-size:1rem"><fmt:formatNumber value="${item.lineTotal}" maxFractionDigits="0"/> đ</div>
                </div>
            </c:forEach>
        </div>
        <div class="card" style="padding:1.5rem">
            <h3 style="margin-top:0;margin-bottom:1rem;font-size:1.1rem;border-bottom:1px solid var(--border);padding-bottom:0.5rem">🧾 Tổng kết thanh toán</h3>
            <div style="display:flex;justify-content:space-between;padding:0.35rem 0"><span style="color:var(--text-muted)">Tạm tính món ăn:</span><span><fmt:formatNumber value="${order.subtotal}" maxFractionDigits="0"/> đ</span></div>
            <div style="display:flex;justify-content:space-between;padding:0.35rem 0"><span style="color:var(--text-muted)">Phí vận chuyển:</span><span><fmt:formatNumber value="${order.deliveryFee}" maxFractionDigits="0"/> đ</span></div>
            <c:if test="${order.discountAmount > 0}"><div style="display:flex;justify-content:space-between;padding:0.35rem 0;color:var(--success,#18794e)"><span>Mã khuyến mãi:</span><span>-<fmt:formatNumber value="${order.discountAmount}" maxFractionDigits="0"/> đ</span></div></c:if>
            <div style="display:flex;justify-content:space-between;padding-top:1rem;margin-top:0.5rem;border-top:1px solid var(--border);font-size:1.25rem;font-weight:800;color:var(--accent-text,#a63a1b)"><span>Tổng cộng:</span><span><fmt:formatNumber value="${order.totalAmount}" maxFractionDigits="0"/> đ</span></div>
        </div>
    </div>
</main>
<script src="<c:url value='/assets/js/orders.js?v=20261017-forms1'/>"></script>
<script src="<c:url value='/assets/js/order-detail.js?v=20261017-forms1'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
