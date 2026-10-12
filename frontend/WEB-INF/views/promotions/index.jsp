<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Ưu đãi & Khuyến mãi" />
<c:set var="pageStyle" value="promotions" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>





<main id="main-content" class="promos-page-wrapper">
    <div class="promos-container">

        <header class="promos-hero-header">
            <h1 class="promos-hero-title">Khuyến mãi & Ưu đãi đặc biệt</h1>
            <p class="promos-hero-lead">
                Chọn mã đang có, xem điều kiện áp dụng và tiết kiệm cho bữa ăn tiếp theo.
            </p>
        </header>

        <!-- Loading State -->
        <div id="promosLoadingState" style="text-align: center; padding: 48px;">
            <div class="btn-spinner" style="border-top-color: var(--crave-brand); border-color: rgba(244,81,30,0.2); width: 36px; height: 36px; margin: 0 auto 16px;"></div>
            <p style="color: var(--crave-text-muted); font-size: 1rem;">Đang tải danh sách chương trình khuyến mãi...</p>
        </div>

        <div id="promosErrorState" class="cart-error-state" hidden role="alert"><h2>Không thể tải khuyến mãi</h2><p id="promosErrorMessage">Vui lòng kiểm tra kết nối và thử lại.</p><button id="promosRetry" class="btn-retry" type="button">Thử lại</button></div>
<p id="promosFeedback" role="status" aria-live="polite"></p>
<!-- Promos Grid -->
        <div id="promosGridContainer" class="promos-grid" style="display: none;">
            <!-- Rendered by JavaScript -->
        </div>
        <nav id="promosPagination" class="customer-pagination" aria-label="Phân trang khuyến mãi" hidden></nav>

        <!-- Empty State -->
        <div id="promosEmptyState" class="cart-empty-state" style="display: none;">
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

<script src="<c:url value='/assets/js/promotions.js'/>"></script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
