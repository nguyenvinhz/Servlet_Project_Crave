<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="pageTitle" value="Ưu đãi & Khuyến mãi"/>
<c:set var="pageStyle" value="promotions"/>
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<main id="main-content" class="promos-page-wrapper">
    <div class="promos-container">
        <header class="promos-hero-header">
            <h1 class="promos-hero-title">Khuyến mãi & Ưu đãi đặc biệt</h1>
            <p class="promos-hero-lead">Chọn mã đang có, xem điều kiện áp dụng và tiết kiệm cho bữa ăn tiếp theo.</p>
        </header>
        <p id="promosFeedback" role="status" aria-live="polite"></p>
        <c:choose>
            <c:when test="${empty promotions}">
                <div class="cart-empty-state">
                    <h2 class="cart-empty-title">Chưa có mã khuyến mãi mới</h2>
                    <p class="cart-empty-desc">Các mã khuyến mãi đang được cập nhật. Bạn có thể khám phá thực đơn và đặt món ngay bây giờ.</p>
                    <a href="<c:url value='/menu'/>" class="btn-explore-menu">Khám phá thực đơn</a>
                </div>
            </c:when>
            <c:otherwise>
                <div class="promos-grid">
                    <c:forEach var="promotion" items="${promotions}">
                        <article class="promo-ticket-card">
                            <div class="promo-ticket-content">
                                <div class="promo-ticket-top">
                                    <span class="promo-ticket-code"><c:out value="${promotion.code}"/></span>
                                    <span class="promo-ticket-value">Giảm <fmt:formatNumber value="${promotion.discountValue}" groupingUsed="true"/>${promotion.discountType == 'PERCENT' ? '%' : ' ₫'}</span>
                                </div>
                                <h2 class="promo-ticket-name"><c:out value="${promotion.name}"/></h2>
                                <p class="promo-ticket-desc"><c:out value="${promotion.description}"/></p>
                                <p class="promo-ticket-desc">Đơn tối thiểu <fmt:formatNumber value="${promotion.minimumOrderValue}" groupingUsed="true"/> ₫<c:if test="${promotion.maximumDiscount > 0}"> · Giảm tối đa <fmt:formatNumber value="${promotion.maximumDiscount}" groupingUsed="true"/> ₫</c:if></p>
                            </div>
                            <div class="promo-ticket-bottom">
                                <button type="button" class="btn-copy-code" data-copy-code="<c:out value='${promotion.code}'/>">Sao chép mã</button>
                                <form action="<c:url value='/cart/promotion'/>" method="post">
                                    <input type="hidden" name="code" value="<c:out value='${promotion.code}'/>">
                                    <button type="submit" class="btn-apply-direct">Dùng ngay</button>
                                </form>
                            </div>
                        </article>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</main>
<script src="<c:url value='/assets/js/promotions.js'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>