<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Đơn hàng của bạn" />
<c:set var="pageStyle" value="order" />
<c:set var="pageBodyClass" value="order-page" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<main class="page-container orders-container" id="main-content">
    <header class="customer-page-heading heading-with-action">
        <div><p class="eyebrow">Theo dõi bữa ngon</p><h1>Đơn hàng của bạn</h1><p>Xem trạng thái và chi tiết các đơn đã đặt.</p></div>
        <a href="<c:url value='/menu'/>" class="button primary">+ Đặt món mới</a>
    </header>
    <div id="orderList" class="orders-list" aria-live="polite"><div class="card order-loading"><p>Đang tải danh sách đơn hàng...</p></div></div>
</main>
<script src="<c:url value='/assets/js/orders.js?v=20261013-palette2'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
