<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="pageTitle" value="Ưu đãi & Khuyến mãi" />
<c:set var="pageStyle" value="promotions" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<input type="hidden" id="pageCartCount" value="<c:out value='${sessionScope.cartItemCount}'/>">





<main id="main-content" class="promos-page-wrapper">
    <div class="promos-container">

        <header class="promos-hero-header">
            <h1 class="promos-hero-title">Khuyến mãi & Ưu đãi đặc biệt</h1>
            <p class="promos-hero-lead">
                Chọn mã đang có, xem điều kiện áp dụng và tiết kiệm cho bữa ăn tiếp theo.
            </p>
        </header>

        <!-- Loading State -->
        <div id="promosLoadingState" style="display:none;text-align: center; padding: 48px;">
            <div class="btn-spinner" style="border-top-color: var(--crave-brand); border-color: rgba(244,81,30,0.2); width: 36px; height: 36px; margin: 0 auto 16px;"></div>
            <p style="color: var(--crave-text-muted); font-size: 1rem;">Đang tải danh sách chương trình khuyến mãi...</p>
        </div>

        <div id="promosErrorState" class="cart-error-state" ${empty promotionsError ? 'hidden' : ''} role="alert"><h2>Không thể tải khuyến mãi</h2><p id="promosErrorMessage">Vui lòng kiểm tra kết nối và thử lại.</p><button id="promosRetry" class="btn-retry" type="button">Thử lại</button></div>
<p id="promosFeedback" role="status" aria-live="polite"></p>
<!-- Promos Grid -->
        <div id="promosGridContainer" class="promos-grid" style="display: ${empty promotionsError and not empty promotions ? 'grid' : 'none'};">
            <c:forEach var="promotion" items="${promotions}">
                <article class="promo-ticket-card">
                    <div class="promo-ticket-content">
                        <div class="promo-ticket-top">
                            <span class="promo-ticket-code"><c:out value="${promotion.code}"/></span>
                            <span class="promo-ticket-value">Giảm <fmt:formatNumber value="${promotion.discountValue}" type="number" groupingUsed="true"/><c:choose><c:when test="${promotion.discountType == 'PERCENT'}">%</c:when><c:otherwise>&nbsp;đ</c:otherwise></c:choose></span>
                        </div>
                        <h2 class="promo-ticket-name"><c:out value="${empty promotion.name ? 'Ưu đãi cho bạn' : promotion.name}"/></h2>
                        <c:if test="${not empty promotion.description}"><p class="promo-ticket-desc"><c:out value="${promotion.description}"/></p></c:if>
                        <p class="promo-ticket-desc"><c:choose><c:when test="${promotion.minimumOrderValue > 0}">Đơn tối thiểu <fmt:formatNumber value="${promotion.minimumOrderValue}" type="number" groupingUsed="true"/>&nbsp;đ</c:when><c:otherwise>Không giới hạn đơn tối thiểu</c:otherwise></c:choose><c:if test="${promotion.maximumDiscount > 0}"> · Giảm tối đa <fmt:formatNumber value="${promotion.maximumDiscount}" type="number" groupingUsed="true"/>&nbsp;đ</c:if></p>
                    </div>
                    <div class="promo-ticket-bottom">
                        <button type="button" class="btn-copy-code" data-code="<c:out value='${promotion.code}'/>">Sao chép mã</button>
                        <c:url var="usePromotionUrl" value="/cart"><c:param name="code" value="${promotion.code}"/></c:url>
                        <a class="btn-apply-direct" href="<c:out value='${usePromotionUrl}'/>">Dùng ngay</a>
                    </div>
                </article>
            </c:forEach>
        </div>

        <!-- Empty State -->
        <div id="promosEmptyState" class="cart-empty-state" style="display: ${empty promotionsError and empty promotions ? 'block' : 'none'};">
            <div class="cart-empty-illustration">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <path d="M8 12h8"></path>
                </svg>
            </div>
            <h2 class="cart-empty-title">Chưa có mã khuyến mãi mới</h2>
            <p class="cart-empty-desc">
                Hiện tại các mã khuyến mãi đang được cập nhật thêm. Bạn có thể ghé thăm thực đơn và đặt những món ăn hấp dẫn ngay bây giờ!
            </p>
            <a href="<c:url value='/menu'/>" class="btn-explore-menu">Khám phá thực đơn</a>
        </div>

    </div>
</main>

<script src="<c:url value='/assets/js/promotions.js?v=20261017-forms1'/>"></script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
