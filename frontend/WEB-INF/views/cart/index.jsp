<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Giỏ hàng của bạn" />
<c:set var="cartPreviewState" value="${param.state == 'empty' or param.state == 'loading' or param.state == 'error' ? param.state : 'layout'}" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/assets/css/cart.css'/>">

<main class="cart-page-wrapper">
    <div class="cart-container">

        <!-- Page Title matching Framer screenshot -->
        <header class="cart-header-section">
            <h1 class="cart-page-title">Your cart</h1>
            <p class="cart-subtitle">Quản lý các món ăn đã chọn và áp dụng mã giảm giá tốt nhất.</p>
            <p class="cart-subtitle">Chức năng đang được hoàn thiện.</p>
            <nav aria-label="Trạng thái giao diện giỏ hàng" style="display: flex; flex-wrap: wrap; gap: 16px;">
                <a class="btn-link-action" href="<c:url value='/cart?state=layout'/>">Khung giỏ hàng</a>
                <a class="btn-link-action" href="<c:url value='/cart?state=empty'/>">Trạng thái rỗng</a>
                <a class="btn-link-action" href="<c:url value='/cart?state=loading'/>">Trạng thái loading</a>
                <a class="btn-link-action" href="<c:url value='/cart?state=error'/>">Trạng thái lỗi</a>
            </nav>
        </header>

        <!-- 1. LOADING STATE (SKELETON SHIMMER) -->
        <div id="cartLoadingState" class="cart-loading-state" style="display: ${cartPreviewState == 'loading' ? 'block' : 'none'};">
            <div class="cart-layout-grid">
                <div class="cart-items-column">
                    <div class="skeleton-card">
                        <div class="skeleton-thumb skeleton-shimmer"></div>
                        <div class="skeleton-text-group">
                            <div class="skeleton-line title skeleton-shimmer"></div>
                            <div class="skeleton-line sub skeleton-shimmer"></div>
                            <div class="skeleton-line price skeleton-shimmer"></div>
                        </div>
                    </div>
                    <div class="skeleton-card">
                        <div class="skeleton-thumb skeleton-shimmer"></div>
                        <div class="skeleton-text-group">
                            <div class="skeleton-line title skeleton-shimmer"></div>
                            <div class="skeleton-line sub skeleton-shimmer"></div>
                            <div class="skeleton-line price skeleton-shimmer"></div>
                        </div>
                    </div>
                </div>
                <div>
                    <div class="order-summary-card" style="box-shadow: none;">
                        <div class="skeleton-line title skeleton-shimmer" style="margin-bottom: 20px;"></div>
                        <div class="skeleton-line sub skeleton-shimmer" style="margin-bottom: 12px;"></div>
                        <div class="skeleton-line sub skeleton-shimmer" style="margin-bottom: 12px;"></div>
                        <div class="summary-divider"></div>
                        <div class="skeleton-line price skeleton-shimmer" style="height: 36px; margin-bottom: 24px;"></div>
                        <div class="skeleton-line skeleton-shimmer" style="height: 52px; border-radius: 12px;"></div>
                    </div>
                </div>
            </div>
        </div>

        <!-- 2. ERROR STATE (TRẠNG THÁI LỖI) -->
        <div id="cartErrorState" class="cart-error-state" style="display: ${cartPreviewState == 'error' ? 'block' : 'none'};">
            <div class="cart-error-icon">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="8" x2="12" y2="12"></line>
                    <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
            </div>
            <h3 class="cart-error-title">Không thể tải thông tin giỏ hàng</h3>
            <p class="cart-error-desc" id="cartErrorMsg">Đã xảy ra lỗi trong quá trình kết nối với máy chủ. Vui lòng thử lại.</p>
            <button type="button" class="btn-retry" id="retryFetchCartBtn" disabled>Thử lại ngay</button>
        </div>

        <!-- 3. EMPTY STATE (TRẠNG THÁI RỖNG) -->
        <div id="cartEmptyState" class="cart-empty-state" style="display: ${cartPreviewState == 'empty' ? 'block' : 'none'};">
            <div class="cart-empty-illustration">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="9" cy="21" r="1"></circle>
                    <circle cx="20" cy="21" r="1"></circle>
                    <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                </svg>
            </div>
            <h2 class="cart-empty-title">Giỏ hàng của bạn đang trống</h2>
            <p class="cart-empty-desc">
                Bạn chưa thêm món ăn nào vào giỏ. Hãy dạo quanh thực đơn phong phú của Crave để tìm những món ngon yêu thích nhé!
            </p>
            <a href="<c:url value='/menu'/>" class="btn-explore-menu">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 3h18v18H3zM9 9h6M9 15h6"></path>
                </svg>
                Khám phá thực đơn ngay
            </a>
        </div>

        <!-- 4. POPULATED CART CONTENT (GIAO DIỆN CHÍNH THEO DESIGN MẪU) -->
        <div id="cartContentWrapper" class="cart-layout-grid" style="display: ${cartPreviewState == 'layout' ? 'grid' : 'none'};">

            <!-- Left Column: Cart Items -->
            <div class="cart-items-column">
                <div id="cartItemsList" class="cart-items-column">
                    <div class="cart-item-card">
                        <div class="cart-item-info">
                            <h3 class="cart-item-title">Danh sách món trong giỏ</h3>
                            <p class="cart-item-options">Thông tin món ăn và tùy chọn tạm thời chưa khả dụng.</p>
                            <div class="cart-item-actions">
                                <div class="cart-stepper">
                                    <button type="button" class="cart-stepper-btn" aria-label="Giảm số lượng" disabled>−</button>
                                    <span class="cart-stepper-val">0</span>
                                    <button type="button" class="cart-stepper-btn" aria-label="Tăng số lượng" disabled>+</button>
                                </div>
                                <button type="button" class="cart-item-remove-btn" disabled>Xóa món</button>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Bottom Toolbar -->
                <div class="cart-bottom-actions">
                    <a href="<c:url value='/menu'/>" class="btn-link-action">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <line x1="19" y1="12" x2="5" y2="12"></line>
                            <polyline points="12 19 5 12 12 5"></polyline>
                        </svg>
                        Chọn thêm món khác
                    </a>
                    <button type="button" class="btn-link-action danger" id="clearCartBtn" disabled>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="3 6 5 6 21 6"></polyline>
                            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                        </svg>
                        Xóa toàn bộ giỏ
                    </button>
                </div>
            </div>

            <!-- Right Column: Order Summary (Matching Screenshot) -->
            <div class="cart-summary-column">
                <div class="order-summary-card">
                    <h2 class="order-summary-title">Order summary</h2>

                    <!-- Subtotal Row -->
                    <div class="summary-row">
                        <span>Subtotal</span>
                        <span id="summarySubtotal" style="font-weight: 600; color: var(--crave-text);">$0.00</span>
                    </div>

                    <!-- Delivery Fee Row -->
                    <div class="summary-row">
                        <span>Delivery fee</span>
                        <span id="summaryDeliveryFee" style="font-weight: 600; color: var(--crave-text);">—</span>
                    </div>

                    <!-- Discount Row (Shown when voucher applied) -->
                    <div class="summary-row discount-row" id="summaryDiscountRow" style="display: none;">
                        <span>Discount</span>
                        <span id="summaryDiscount">-$0.00</span>
                    </div>

                    <div class="summary-divider"></div>

                    <!-- Total Row (Matching Screenshot: "Total $12.82") -->
                    <div class="summary-total-row">
                        <span class="summary-total-label">Total</span>
                        <span class="summary-total-value" id="summaryTotal">$0.00</span>
                    </div>

                    <!-- Voucher / Promotion Area (Khu vực nhập voucher) -->
                    <div class="voucher-section">
                        <div class="voucher-section-title">
                            <span>Mã khuyến mãi (Voucher)</span>
                            <a href="<c:url value='/promotions'/>" class="voucher-view-all-link" id="viewPromosBtn">Xem mã có sẵn</a>
                        </div>

                        <div class="voucher-input-group">
                            <input type="text" id="voucherCodeInput" class="voucher-input"
                                   placeholder="Nhập mã ưu đãi (VD: WELCOME10)" autocomplete="off" disabled>
                            <button type="button" id="voucherApplyBtn" class="voucher-apply-btn" disabled>
                                Áp dụng
                            </button>
                        </div>

                        <!-- Applied Voucher Chip -->
                        <div id="voucherAppliedChip" class="voucher-applied-chip" style="display: none;">
                            <div class="voucher-chip-info">
                                <span class="voucher-code-badge" id="voucherAppliedCode">CODE</span>
                                <span class="voucher-discount-text" id="voucherAppliedDiscount">-0 đ</span>
                            </div>
                            <button type="button" class="voucher-remove-btn" id="voucherRemoveBtn" title="Hủy mã khuyến mãi" disabled>
                                &times;
                            </button>
                        </div>

                        <!-- Feedback Message -->
                        <div id="voucherFeedback" class="voucher-feedback"></div>
                    </div>

                    <!-- Checkout Button (Matching Screenshot: Orange Button) -->
                    <button type="button" class="btn-checkout" id="proceedCheckoutBtn" disabled>
                        Proceed to Checkout
                    </button>
                </div>
            </div>

        </div>

    </div>
</main>

<!-- PROMOTIONS DRAWER / MODAL -->
<div id="promosModal" class="promo-modal-backdrop" aria-hidden="true">
    <div class="promo-modal-dialog">
        <div class="promo-modal-header">
            <h3 class="promo-modal-title">Mã khuyến mãi dành cho bạn</h3>
            <button type="button" class="promo-modal-close" id="promosModalClose" aria-label="Đóng" disabled>&times;</button>
        </div>
        <p style="color: var(--crave-text-muted); font-size: 0.9rem; margin-top: -10px; margin-bottom: 20px;">
            Chọn mã ưu đãi phù hợp với đơn hàng của bạn để được áp dụng giảm giá ngay lập tức:
        </p>
        <div id="promosModalList" class="promo-list">
            <!-- Khu vực danh sách khuyến mãi. -->
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
