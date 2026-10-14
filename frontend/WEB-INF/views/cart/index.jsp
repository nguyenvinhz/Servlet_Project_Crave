<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Giỏ hàng của bạn" />
<c:set var="pageStyle" value="cart" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>



<main id="main-content" class="cart-page-wrapper">
    <div class="cart-container">

        <!-- Page Title matching Framer screenshot -->
        <header class="cart-header-section">
            <h1 class="cart-page-title">Giỏ hàng của bạn</h1>
            <p class="cart-subtitle">Quản lý các món ăn đã chọn và áp dụng mã giảm giá tốt nhất.</p>
        </header>

        <!-- 1. LOADING STATE (SKELETON SHIMMER) -->
        <div id="cartLoadingState" class="cart-loading-state">
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
        <div id="cartErrorState" class="cart-error-state" style="display: none;">
            <div class="cart-error-icon">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="8" x2="12" y2="12"></line>
                    <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
            </div>
            <h3 class="cart-error-title">Không thể tải thông tin giỏ hàng</h3>
            <p class="cart-error-desc" id="cartErrorMsg">Đã xảy ra lỗi trong quá trình kết nối với máy chủ. Vui lòng thử lại.</p>
            <button type="button" class="btn-retry" id="retryFetchCartBtn">Thử lại ngay</button>
        </div>

        <!-- 3. EMPTY STATE (TRẠNG THÁI RỖNG) -->
        <div id="cartEmptyState" class="cart-empty-state" style="display: none;">
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
        <div id="cartContentWrapper" class="cart-layout-grid" style="display: none;">

            <!-- Left Column: Cart Items -->
            <div class="cart-items-column">
                <div id="cartItemsList" class="cart-items-column">
                    <!-- Populated dynamically by cart.js -->
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
                    <button type="button" class="btn-link-action danger" id="clearCartBtn">
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
                    <h2 class="order-summary-title">Tóm tắt đơn hàng</h2>

                    <!-- Subtotal Row -->
                    <div class="summary-row">
                        <span>Tạm tính</span>
                        <span id="summarySubtotal" style="font-weight: 600; color: var(--crave-text);">0 đ</span>
                    </div>

                    <!-- Delivery Fee Row -->
                    <div class="summary-row">
                        <span>Phí giao hàng</span>
                        <span id="summaryDeliveryFee" style="font-weight: 600; color: var(--crave-text);">Tính khi thanh toán</span>
                    </div>

                    <!-- Discount Row (Shown when voucher applied) -->
                    <div class="summary-row discount-row" id="summaryDiscountRow" style="display: none;">
                        <span>Giảm giá</span>
                        <span id="summaryDiscount">-0 đ</span>
                    </div>

                    <div class="summary-divider"></div>

                    <!-- Total Row (Matching Screenshot: "Total $12.82") -->
                    <div class="summary-total-row">
                        <span class="summary-total-label">Tiền món sau giảm</span>
                        <span class="summary-total-value" id="summaryTotal">0 đ</span>
                    </div>

                    <!-- Voucher / Promotion Area (Khu vực nhập voucher) -->
                    <p class="cart-shipping-hint">Phí giao hàng được cập nhật sau khi chọn điểm nhận ở bước thanh toán.</p>
                    <div class="voucher-section">
                        <div class="voucher-section-title">
                            <span>Mã khuyến mãi</span>
                            <a href="<c:url value='/promotions'/>" class="voucher-view-all-link" id="viewPromosBtn">Xem mã có sẵn</a>
                        </div>

                        <div class="voucher-input-group">
                            <input type="text" id="voucherCodeInput" class="voucher-input"
                                   placeholder="Nhập mã ưu đãi" aria-label="Mã khuyến mãi" autocomplete="off">
                            <button type="button" id="voucherApplyBtn" class="voucher-apply-btn">
                                Áp dụng
                            </button>
                        </div>

                        <!-- Applied Voucher Chip -->
                        <div id="voucherAppliedChip" class="voucher-applied-chip" style="display: none;">
                            <div class="voucher-chip-info">
                                <span class="voucher-code-badge" id="voucherAppliedCode">CODE</span>
                                <span class="voucher-discount-text" id="voucherAppliedDiscount">-0 đ</span>
                            </div>
                            <button type="button" class="voucher-remove-btn" id="voucherRemoveBtn" title="Hủy mã khuyến mãi">
                                &times;
                            </button>
                        </div>

                        <!-- Feedback Message -->
                        <div id="voucherFeedback" class="voucher-feedback" role="status" aria-live="polite"></div>
                    </div>

                    <!-- Checkout Button (Matching Screenshot: Orange Button) -->
                    <button type="button" class="btn-checkout" id="proceedCheckoutBtn">
                        Tiếp tục thanh toán
                    </button>
                </div>
            </div>

        </div>

    </div>
</main>

<!-- PROMOTIONS DRAWER / MODAL -->
<div id="promosModal" class="promo-modal-backdrop" aria-hidden="true">
    <div class="promo-modal-dialog" role="dialog" aria-modal="true" aria-labelledby="promos-modal-title" tabindex="-1">
        <div class="promo-modal-header">
            <h3 id="promos-modal-title" class="promo-modal-title">Mã khuyến mãi dành cho bạn</h3>
            <button type="button" class="promo-modal-close" id="promosModalClose" aria-label="Đóng">&times;</button>
        </div>
        <p style="color: var(--crave-text-muted); font-size: 0.9rem; margin-top: -10px; margin-bottom: 20px;">
            Chọn mã ưu đãi phù hợp với đơn hàng của bạn để được áp dụng giảm giá ngay lập tức:
        </p>
        <div id="promosModalList" class="promo-list">
            <!-- Loaded dynamically by cart.js -->
        </div>
    </div>
</div>

<dialog id="cartItemEditor" class="cart-editor" aria-labelledby="cartEditorTitle">
    <form id="cartItemEditorForm">
        <div class="promo-modal-header">
            <h3 id="cartEditorTitle" class="promo-modal-title">Chỉnh sửa món</h3>
            <button type="button" id="cartEditorClose" class="promo-modal-close" aria-label="Đóng">&times;</button>
        </div>
        <p id="cartEditorStatus" role="status" aria-live="polite"></p>
        <div id="cartEditorFields" hidden>
            <div id="cartEditorOptions"></div>
            <label class="cart-editor-field" for="cartEditorQuantity">Số lượng
                <input id="cartEditorQuantity" type="number" min="1" max="100" step="1" required>
            </label>
            <label class="cart-editor-field" for="cartEditorNote">Ghi chú cho món
                <textarea id="cartEditorNote" maxlength="255" rows="2" placeholder="Ví dụ: ít cay, không hành"></textarea>
            </label>
            <div class="summary-total-row"><span>Thành tiền</span><strong id="cartEditorTotal"></strong></div>
        </div>
        <div class="cart-editor-actions">
            <button type="button" id="cartEditorCancel" class="voucher-apply-btn">Hủy</button>
            <button type="submit" id="cartEditorSave" class="btn-checkout" disabled>Lưu thay đổi</button>
        </div>
    </form>
</dialog>

<script src="<c:url value='/assets/js/cart.js?v=20261015-order1'/>"></script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
