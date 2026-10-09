<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Ưu đãi & Khuyến mãi" />
<c:set var="promosPreviewState" value="${param.state == 'empty' or param.state == 'loading' or param.state == 'error' ? param.state : 'layout'}" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/assets/css/cart.css'/>">

<style>
.promos-page-wrapper {
    background-color: var(--crave-bg-page);
    min-height: calc(100vh - 160px);
    padding: 48px 24px 80px;
}

.promos-container {
    max-width: 1040px;
    margin: 0 auto;
}

.promos-hero-header {
    text-align: center;
    max-width: 640px;
    margin: 0 auto 48px;
}

.promos-hero-title {
    font-family: var(--crave-font-heading);
    font-size: 2.75rem;
    font-weight: 800;
    color: var(--crave-text);
    margin: 0 0 12px 0;
    letter-spacing: -0.02em;
}

.promos-hero-lead {
    color: var(--crave-text-muted);
    font-size: 1.05rem;
    line-height: 1.5;
    margin: 0;
}

.promos-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 24px;
}

.promo-ticket-card {
    background: #fff;
    border: 1px dashed #e3cbbe;
    border-radius: var(--crave-radius-xl);
    padding: 24px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    box-shadow: var(--crave-shadow-sm);
    transition: all 0.25s ease;
    position: relative;
    overflow: hidden;
}

.promo-ticket-card::before,
.promo-ticket-card::after {
    content: '';
    position: absolute;
    top: 50%;
    width: 20px;
    height: 20px;
    background-color: var(--crave-bg-page);
    border-radius: 50%;
    transform: translateY(-50%);
}

.promo-ticket-card::before { left: -10px; }
.promo-ticket-card::after { right: -10px; }

.promo-ticket-card:hover {
    border-color: var(--crave-brand);
    box-shadow: var(--crave-shadow-md);
    transform: translateY(-2px);
}

.promo-ticket-top {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
}

.promo-ticket-code {
    font-family: var(--crave-font-heading);
    font-size: 1.25rem;
    font-weight: 800;
    color: var(--crave-brand);
    background: var(--crave-brand-light);
    padding: 4px 12px;
    border-radius: 8px;
    letter-spacing: 0.05em;
    display: inline-block;
}

.promo-ticket-value {
    font-family: var(--crave-font-heading);
    font-size: 1.4rem;
    font-weight: 800;
    color: var(--crave-text);
}

.promo-ticket-name {
    font-family: var(--crave-font-heading);
    font-size: 1.1rem;
    font-weight: 700;
    color: var(--crave-text);
    margin: 0 0 8px 0;
}

.promo-ticket-desc {
    color: var(--crave-text-muted);
    font-size: 0.9rem;
    line-height: 1.45;
    margin: 0 0 20px 0;
}

.promo-ticket-bottom {
    display: flex;
    gap: 10px;
    align-items: center;
    border-top: 1px dashed var(--crave-border);
    padding-top: 16px;
}

.btn-copy-code {
    flex: 1;
    background: var(--crave-bg-page);
    color: var(--crave-text);
    border: 1px solid var(--crave-border);
    border-radius: 10px;
    padding: 10px 14px;
    font-weight: 700;
    font-size: 0.88rem;
    cursor: pointer;
    transition: all 0.2s ease;
}

.btn-copy-code:hover {
    background: #fff;
    border-color: var(--crave-text);
}

.btn-apply-direct {
    flex: 1.3;
    background: var(--crave-brand);
    color: #fff;
    border: none;
    border-radius: 10px;
    padding: 10px 14px;
    font-weight: 800;
    font-size: 0.88rem;
    cursor: pointer;
    text-align: center;
    text-decoration: none;
    transition: all 0.2s ease;
}

.btn-apply-direct:hover {
    background: var(--crave-brand-hover);
    color: #fff;
    text-decoration: none;
}
</style>

<main class="promos-page-wrapper">
    <div class="promos-container">

        <header class="promos-hero-header">
            <h1 class="promos-hero-title">Khuyến mãi & Ưu đãi đặc biệt</h1>
            <p class="promos-hero-lead">
                Áp dụng các mã voucher giảm giá độc quyền từ Crave để thưởng thức trọn vẹn bữa ăn ngon với chi phí tối ưu nhất!
            </p>
            <p class="promos-hero-lead">Chức năng đang được hoàn thiện.</p>
            <nav aria-label="Trạng thái giao diện khuyến mãi" style="display: flex; justify-content: center; flex-wrap: wrap; gap: 16px; margin-top: 16px;">
                <a class="btn-link-action" href="<c:url value='/promotions?state=layout'/>">Khung khuyến mãi</a>
                <a class="btn-link-action" href="<c:url value='/promotions?state=empty'/>">Trạng thái rỗng</a>
                <a class="btn-link-action" href="<c:url value='/promotions?state=loading'/>">Trạng thái loading</a>
                <a class="btn-link-action" href="<c:url value='/promotions?state=error'/>">Trạng thái lỗi</a>
            </nav>
        </header>

        <!-- Loading State -->
        <div id="promosLoadingState" style="display: ${promosPreviewState == 'loading' ? 'block' : 'none'}; text-align: center; padding: 48px;">
            <div class="btn-spinner" style="border-top-color: var(--crave-brand); border-color: rgba(244,81,30,0.2); width: 36px; height: 36px; margin: 0 auto 16px;"></div>
            <p style="color: var(--crave-text-muted); font-size: 1rem;">Đang tải danh sách chương trình khuyến mãi...</p>
        </div>

        <!-- Promos Grid -->
        <div id="promosGridContainer" class="promos-grid" style="display: ${promosPreviewState == 'layout' ? 'grid' : 'none'};">
            <article class="promo-ticket-card">
                <div>
                    <div class="promo-ticket-top">
                        <span class="promo-ticket-code">MÃ ƯU ĐÃI</span>
                        <span class="promo-ticket-value">—</span>
                    </div>
                    <h3 class="promo-ticket-name">Thông tin khuyến mãi</h3>
                    <p class="promo-ticket-desc">Thông tin ưu đãi và điều kiện áp dụng tạm thời chưa khả dụng.</p>
                </div>
                <div class="promo-ticket-bottom">
                    <button type="button" class="btn-copy-code" disabled>Sao chép mã</button>
                    <button type="button" class="btn-apply-direct" disabled>Dùng ngay</button>
                </div>
            </article>
        </div>

        <!-- Error State -->
        <div id="promosErrorState" class="cart-error-state" style="display: ${promosPreviewState == 'error' ? 'block' : 'none'};">
            <h3 class="cart-error-title">Không thể tải thông tin khuyến mãi</h3>
            <p id="promosErrorMsg" class="cart-error-desc">Đã xảy ra lỗi kết nối. Vui lòng thử lại.</p>
            <button type="button" class="btn-retry" disabled>Thử lại ngay</button>
        </div>

        <!-- Empty State -->
        <div id="promosEmptyState" class="cart-empty-state" style="display: ${promosPreviewState == 'empty' ? 'block' : 'none'};">
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

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
