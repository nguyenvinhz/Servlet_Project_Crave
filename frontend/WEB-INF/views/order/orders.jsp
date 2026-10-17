<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="labels.jspf" %>
<c:set var="pageTitle" value="Đơn hàng của bạn" />
<c:set var="pageStyle" value="order" />
<c:set var="pageBodyClass" value="order-page" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<main class="page-container orders-container" id="main-content">
    <header class="customer-page-heading heading-with-action">
        <div><p class="eyebrow">Theo dõi bữa ngon</p><h1>Đơn hàng của bạn</h1><p>Xem trạng thái và chi tiết các đơn đã đặt.</p></div>
        <a href="<c:url value='/menu'/>" class="button primary">+ Đặt món mới</a>
    </header>
    <c:if test="${not empty sessionScope.orderError}"><div class="alert error" style="display:block" role="alert"><c:out value="${sessionScope.orderError}"/></div><c:remove var="orderError" scope="session"/></c:if>
    <div id="orderList" class="orders-list" aria-live="polite">
        <c:if test="${empty orders}">
            <div class="card" style="padding:3rem 1.5rem;text-align:center">
                <div style="font-size:3rem;margin-bottom:1rem">🍽️</div><h3 style="margin-bottom:0.5rem;font-size:1.25rem">Bạn chưa có đơn hàng nào</h3>
                <p style="color:var(--text-muted);margin-bottom:1.5rem;font-size:0.95rem">Hãy khám phá các món ngon đặc sắc tại Crave và đặt món ngay hôm nay!</p>
                <a href="<c:url value='/menu'/>" class="btn-primary" style="display:inline-block;width:auto;margin:0 auto;text-decoration:none;padding:0.75rem 1.5rem">Khám phá thực đơn</a>
            </div>
        </c:if>
        <c:forEach var="order" items="${orders}">
            <div class="order-card">
                <div class="order-card-info"><h3>Đơn hàng #<c:out value="${order.orderId}"/></h3><p>Ngày đặt: <time data-date="<c:out value='${order.orderedAt}'/>"><c:out value="${order.orderedAt}"/></time></p><p>Hình thức: ${order.fulfillmentType == 'DELIVERY' ? 'Giao tận nơi' : 'Lấy tại quán'}</p></div>
                <div class="order-card-right"><div class="price"><fmt:formatNumber value="${order.totalAmount}" maxFractionDigits="0"/> đ</div><span class="order-status-badge ${orderBadges[order.status.toString()]}"><c:out value="${orderLabels[order.status.toString()]}"/></span>
                    <div style="margin-top:0.5rem;display:flex;align-items:center;justify-content:flex-end;gap:0.75rem">
                        <c:if test="${order.status == 'PENDING_CONFIRMATION' || order.status == 'PREPARING'}"><button type="button" class="btn-cancel-order" data-cancel-order="<c:out value='${order.orderId}'/>" title="Hủy đơn hàng này">✕ Hủy đơn</button></c:if>
                        <c:url var="detailUrl" value="/orders/${order.orderId}"/><a href="${detailUrl}" style="color:var(--accent-text,#a63a1b);font-weight:600;font-size:0.85rem;text-decoration:none">Xem chi tiết →</a>
                    </div>
                </div>
            </div>
        </c:forEach>
    </div>
</main>
<script src="<c:url value='/assets/js/orders.js?v=20261017-forms1'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
