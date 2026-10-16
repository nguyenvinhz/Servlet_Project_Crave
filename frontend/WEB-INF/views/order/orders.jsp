<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<c:set var="pageTitle" value="Đơn hàng của bạn"/>
<c:set var="pageStyle" value="order"/>
<c:set var="pageBodyClass" value="order-page"/>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container orders-container" id="main-content">
    <header class="customer-page-heading heading-with-action">
        <div>
            <p class="eyebrow">Theo dõi bữa ngon</p>
            <h1>Đơn hàng của bạn</h1>
            <p>Xem trạng thái và chi tiết các đơn đã đặt.</p>
        </div>
        <a href="<c:url value='/menu'/>" class="button primary">+ Đặt món mới</a>
    </header>
    <c:if test="${not empty flashError}">
        <p class="form-error" role="alert"><c:out value="${flashError}"/></p>
    </c:if>
    <c:if test="${not empty flashMessage}">
        <p role="status"><c:out value="${flashMessage}"/></p>
    </c:if>
    <div id="orderList" class="orders-list">
        <c:forEach var="order" items="${orders}">
            <article class="card order-card">
                <h2><a href="<c:url value='/orders/${order.orderId}'/>">Đơn <c:out value="${order.orderId}"/></a></h2>
                <p><c:out value="${order.orderedAt}"/> · <c:out value="${order.fulfillmentType}"/></p>
                <p>Trạng thái: <strong><c:out value="${order.status}"/></strong> · Thanh toán: <c:out value="${order.paymentStatus}"/></p>
                <p><fmt:formatNumber value="${order.totalAmount}"/> đ</p>
            </article>
        </c:forEach>
        <c:if test="${empty orders and empty flashError}">
            <div class="card">
                <p>Bạn chưa có đơn hàng.</p>
                <a href="<c:url value='/menu'/>">Khám phá thực đơn</a>
            </div>
        </c:if>
    </div>
</main>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>