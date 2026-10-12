<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Chi tiết đơn hàng" />
<c:set var="pageStyle" value="order" />
<c:set var="pageBodyClass" value="order-page" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<main class="page-container detail-container" id="main-content">
    <a href="<c:url value='/orders'/>" class="btn-back">← Quay lại đơn hàng của bạn</a>
    <div id="orderContent" aria-live="polite"><div class="card order-loading"><p>Đang tải chi tiết đơn hàng...</p></div></div>
</main>
<script src="<c:url value='/assets/js/order-detail.js'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
