<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="pageTitle" value="Giỏ hàng của bạn" />
<c:set var="pageStyle" value="cart" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<input type="hidden" id="pageCartCount" value="<c:out value='${sessionScope.cartItemCount}'/>">
<div id="cartFlash" hidden data-message="<c:out value='${sessionScope.cartMessage}'/>" data-type="<c:out value='${sessionScope.cartMessageType}'/>"></div>
<c:remove var="cartMessage" scope="session"/><c:remove var="cartMessageType" scope="session"/>
<c:set var="voucherErrorText" value="${empty requestScope.voucherErrorMessage ? sessionScope.voucherErrorMessage : requestScope.voucherErrorMessage}"/>
<c:remove var="voucherErrorMessage" scope="session"/>



<main id="main-content" class="cart-page-wrapper">
    <div class="cart-container">

        <!-- Page Title matching Framer screenshot -->
        <header class="cart-header-section">
            <h1 class="cart-page-title">Giỏ hàng của bạn</h1>
            <p class="cart-subtitle">Quản lý các món ăn đã chọn và áp dụng mã giảm giá tốt nhất.</p>
        </header>

        <!-- 1. LOADING STATE (SKELETON SHIMMER) -->
        <div id="cartLoadingState" class="cart-loading-state" style="display:none;">
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
        <div id="cartErrorState" class="cart-error-state" style="display: ${not empty cartLoadError ? 'block' : 'none'};">
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
        <div id="cartEmptyState" class="cart-empty-state" style="display: ${empty cartLoadError and empty cart.items ? 'block' : 'none'};">
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
        <div id="cartContentWrapper" class="cart-layout-grid" style="display: ${empty cartLoadError and not empty cart.items ? 'grid' : 'none'};">

            <!-- Left Column: Cart Items -->
            <div class="cart-items-column">
                <div id="cartItemsList" class="cart-items-column">
                    <c:forEach var="item" items="${cart.items}">
                        <c:set var="itemImage" value="${empty item.imageUrl ? pageContext.request.contextPath.concat('/assets/images/food-placeholder.svg') : item.imageUrl}"/>
                        <c:if test="${not empty item.imageUrl and not item.imageUrl.startsWith('http') and not item.imageUrl.startsWith('/')}">
                            <c:set var="itemImage" value="${pageContext.request.contextPath.concat('/').concat(item.imageUrl)}"/>
                        </c:if>
                        <div class="cart-item-card" data-id="<c:out value='${item.cartItemId}'/>">
                            <div class="cart-item-image-wrapper">
                                <img src="<c:out value='${itemImage}'/>" alt="<c:out value='${item.foodName}'/>" class="cart-item-image"
                                     onerror="this.onerror=null;this.src='<c:url value='/assets/images/food-placeholder.svg'/>'">
                            </div>
                            <div class="cart-item-info">
                                <h3 class="cart-item-title"><button type="button" class="cart-item-title-button btn-edit" data-id="<c:out value='${item.cartItemId}'/>"><c:out value="${item.foodName}"/></button></h3>
                                <c:if test="${not empty item.options}"><p class="cart-item-options"><c:forEach var="option" items="${item.options}" varStatus="status"><c:if test="${not status.first}"> · </c:if><c:out value="${option.name}"/></c:forEach></p></c:if>
                                <c:if test="${not empty item.note}"><p class="cart-item-note">💬 "<c:out value="${item.note}"/>"</p></c:if>
                                <div class="cart-item-actions">
                                    <button type="button" class="cart-item-edit-btn btn-edit" data-id="<c:out value='${item.cartItemId}'/>">Chỉnh sửa món</button>
                                    <div class="cart-stepper">
                                        <button type="button" class="cart-stepper-btn btn-dec" data-id="<c:out value='${item.cartItemId}'/>" data-qty="${item.quantity - 1}" aria-label="Giảm số lượng">−</button>
                                        <span class="cart-stepper-val"><c:out value="${item.quantity}"/></span>
                                        <button type="button" class="cart-stepper-btn btn-inc" data-id="<c:out value='${item.cartItemId}'/>" data-qty="${item.quantity + 1}" aria-label="Tăng số lượng" ${item.quantity >= 100 ? 'disabled' : ''}>+</button>
                                    </div>
                                    <button type="button" class="cart-item-remove-btn btn-remove" data-id="<c:out value='${item.cartItemId}'/>" title="Xóa món khỏi giỏ">
                                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                            <polyline points="3 6 5 6 21 6"></polyline>
                                            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                        </svg>
                                    </button>
                                </div>
                            </div>
                            <div class="cart-item-price-tag"><fmt:formatNumber value="${item.unitPrice}" type="number" groupingUsed="true"/> đ × <c:out value="${item.quantity}"/></div>
                        </div>
                    </c:forEach>
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
                        <span id="summarySubtotal" style="font-weight: 600; color: var(--crave-text);"><fmt:formatNumber value="${cart.subtotal}" type="number" groupingUsed="true"/> đ</span>
                    </div>

                    <!-- Delivery Fee Row -->
                    <div class="summary-row">
                        <span>Phí giao hàng</span>
                        <span id="summaryDeliveryFee" style="font-weight: 600; color: var(--crave-text);">Tính khi thanh toán</span>
                    </div>

                    <!-- Discount Row (Shown when voucher applied) -->
                    <div class="summary-row discount-row" id="summaryDiscountRow" style="display: ${not empty appliedVoucher and appliedVoucher.discountAmount > 0 ? 'flex' : 'none'};">
                        <span>Giảm giá</span>
                        <span id="summaryDiscount">-<fmt:formatNumber value="${appliedVoucher.discountAmount}" type="number" groupingUsed="true"/> đ</span>
                    </div>

                    <div class="summary-divider"></div>

                    <!-- Total Row (Matching Screenshot: "Total $12.82") -->
                    <div class="summary-total-row">
                        <span class="summary-total-label">Tiền món sau giảm</span>
                        <span class="summary-total-value" id="summaryTotal"><fmt:formatNumber value="${cartTotal}" type="number" groupingUsed="true"/> đ</span>
                    </div>

                    <!-- Voucher / Promotion Area (Khu vực nhập voucher) -->
                    <p class="cart-shipping-hint">Phí giao hàng được cập nhật sau khi chọn điểm nhận ở bước thanh toán.</p>
                    <div class="voucher-section">
                        <div class="voucher-section-title">
                            <span>Mã khuyến mãi</span>
                            <a href="<c:url value='/promotions'/>" class="voucher-view-all-link" id="viewPromosBtn">Xem mã có sẵn</a>
                        </div>

                        <form class="voucher-input-group" method="post" action="<c:url value='/promotions/apply'/>" style="${not empty appliedVoucher ? 'display:none' : ''}">
                            <input type="text" id="voucherCodeInput" name="code" class="voucher-input"
                                   placeholder="Nhập mã ưu đãi" aria-label="Mã khuyến mãi" autocomplete="off" required>
                            <button type="submit" id="voucherApplyBtn" class="voucher-apply-btn">Áp dụng</button>
                        </form>

                        <!-- Applied Voucher Chip -->
                        <form id="voucherAppliedChip" class="voucher-applied-chip" method="post" action="<c:url value='/promotions/remove'/>" style="display: ${not empty appliedVoucher ? 'flex' : 'none'};">
                            <div class="voucher-chip-info">
                                <span class="voucher-code-badge" id="voucherAppliedCode"><c:out value="${appliedVoucher.promotion.code}"/></span>
                                <span class="voucher-discount-text" id="voucherAppliedDiscount">-<fmt:formatNumber value="${appliedVoucher.discountAmount}" type="number" groupingUsed="true"/> đ</span>
                            </div>
                            <button type="submit" class="voucher-remove-btn" id="voucherRemoveBtn" title="Hủy mã khuyến mãi">&times;</button>
                        </form>

                        <!-- Feedback Message -->
                        <div id="voucherFeedback" class="voucher-feedback ${not empty voucherErrorText ? 'error' : ''}" role="status" aria-live="polite"><c:out value="${voucherErrorText}"/></div>
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
            <c:if test="${not empty promotionsError}"><p><c:out value="${promotionsError}"/></p></c:if>
            <c:if test="${empty promotions and empty promotionsError}"><div style="text-align: center; padding: 24px; color: var(--crave-text-muted);"><p>Hiện không có mã khuyến mãi công khai nào.</p></div></c:if>
            <c:forEach var="promotion" items="${promotions}">
                <div class="promo-card">
                    <div>
                        <span class="promo-badge-tag"><c:out value="${promotion.code}"/></span>
                        <h4 class="promo-card-title"><c:out value="${promotion.name}"/></h4>
                        <p class="promo-card-desc">
                            Giảm <c:choose><c:when test="${promotion.discountType == 'PERCENT'}"><fmt:formatNumber value="${promotion.discountValue}" maxFractionDigits="0"/>% <c:if test="${promotion.maximumDiscount > 0}">(tối đa <fmt:formatNumber value="${promotion.maximumDiscount}" type="number" groupingUsed="true"/> đ)</c:if></c:when><c:otherwise><fmt:formatNumber value="${promotion.discountValue}" type="number" groupingUsed="true"/> đ</c:otherwise></c:choose>
                            · <c:choose><c:when test="${promotion.minimumOrderValue > 0}">Đơn tối thiểu <fmt:formatNumber value="${promotion.minimumOrderValue}" type="number" groupingUsed="true"/> đ</c:when><c:otherwise>Mọi đơn hàng</c:otherwise></c:choose>
                        </p>
                    </div>
                    <form method="post" action="<c:url value='/promotions/apply'/>" style="margin:0;display:contents">
                        <input type="hidden" name="code" value="<c:out value='${promotion.code}'/>">
                        <button type="submit" class="btn-apply-promo-card">Áp dụng</button>
                    </form>
                </div>
            </c:forEach>
        </div>
    </div>
</div>

<dialog id="cartItemEditor" class="cart-editor" aria-labelledby="cartEditorTitle" data-open="${not empty editingItem}">
    <form id="cartItemEditorForm" method="post" action="<c:url value='/cart/action'/>" data-base-price="<c:out value='${editingFood.price}'/>">
        <input type="hidden" name="action" value="update">
        <input type="hidden" name="cartItemId" value="<c:out value='${editingItem.cartItemId}'/>">
        <input type="hidden" name="updateOptions" value="true">
        <div class="promo-modal-header">
            <h3 id="cartEditorTitle" class="promo-modal-title">Chỉnh sửa <c:out value="${editingItem.foodName}"/></h3>
            <button type="button" id="cartEditorClose" class="promo-modal-close" aria-label="Đóng">&times;</button>
        </div>
        <p id="cartEditorStatus" role="status" aria-live="polite"><c:choose><c:when test="${not empty editorError}"><c:out value="${editorError}"/></c:when><c:when test="${editingFood.status != 'AVAILABLE'}">Món ăn hiện đã ngừng phục vụ. Vui lòng chọn món khác.</c:when><c:when test="${editorMissingOptions}">Một số tùy chọn cũ đã ngừng áp dụng. Vui lòng kiểm tra các lựa chọn trước khi lưu.</c:when><c:when test="${empty editingFood.options}">Món này không có tùy chọn thêm.</c:when><c:otherwise>Chọn món thêm hoặc thay đổi tùy chọn cho món này.</c:otherwise></c:choose></p>
        <div id="cartEditorFields" ${empty editingFood or editingFood.status != 'AVAILABLE' ? 'hidden' : ''}>
            <div id="cartEditorOptions">
                <c:forEach var="type" items="${optionTypes}">
                    <c:set var="hasOptions" value="false"/><c:set var="hasSelectedOption" value="false"/>
                    <c:forEach var="option" items="${editingFood.options}">
                        <c:if test="${option.optionType == type}"><c:set var="hasOptions" value="true"/><c:if test="${editingOptionIds.contains(option.id)}"><c:set var="hasSelectedOption" value="true"/></c:if></c:if>
                    </c:forEach>
                    <c:if test="${hasOptions}">
                        <c:set var="singleOption" value="${type == 'SIZE' or type == 'SUGAR_LEVEL' or type == 'ICE_LEVEL'}"/>
                        <fieldset class="cart-editor-group">
                            <legend><c:choose><c:when test="${type == 'SIZE'}">Kích cỡ</c:when><c:when test="${type == 'TOPPING'}">Món thêm</c:when><c:when test="${type == 'SUGAR_LEVEL'}">Mức đường</c:when><c:when test="${type == 'ICE_LEVEL'}">Mức đá</c:when><c:otherwise>Tùy chọn khác</c:otherwise></c:choose></legend>
                            <c:if test="${singleOption}"><label class="cart-editor-option"><input type="radio" name="edit_${type}" value="" data-extra="0" ${not hasSelectedOption ? 'checked' : ''}><span>${type == 'SIZE' ? 'Tiêu chuẩn' : 'Không chọn'}</span><span>+0 đ</span></label></c:if>
                            <c:forEach var="option" items="${editingFood.options}">
                                <c:if test="${option.optionType == type}">
                                    <label class="cart-editor-option"><input type="${singleOption ? 'radio' : 'checkbox'}" name="edit_${type}" value="<c:out value='${option.id}'/>" data-extra="<c:out value='${option.extraPrice}'/>" ${editingOptionIds.contains(option.id) ? 'checked' : ''}><span><c:out value="${option.name}"/></span><span>+<fmt:formatNumber value="${option.extraPrice}" type="number" groupingUsed="true"/> đ</span></label>
                                </c:if>
                            </c:forEach>
                        </fieldset>
                    </c:if>
                </c:forEach>
            </div>
            <label class="cart-editor-field" for="cartEditorQuantity">Số lượng
                <input id="cartEditorQuantity" name="quantity" type="number" min="1" max="100" step="1" required value="<c:out value='${editingItem.quantity}'/>">
            </label>
            <label class="cart-editor-field" for="cartEditorNote">Ghi chú cho món
                <textarea id="cartEditorNote" name="note" maxlength="255" rows="2" placeholder="Ví dụ: ít cay, không hành"><c:out value="${editingItem.note}"/></textarea>
            </label>
            <div class="summary-total-row"><span>Thành tiền</span><strong id="cartEditorTotal"></strong></div>
        </div>
        <div class="cart-editor-actions">
            <button type="button" id="cartEditorCancel" class="voucher-apply-btn">Hủy</button>
            <button type="submit" id="cartEditorSave" class="btn-checkout" ${empty editingFood or editingFood.status != 'AVAILABLE' ? 'disabled' : ''}>Lưu thay đổi</button>
        </div>
    </form>
</dialog>

<script src="<c:url value='/assets/js/cart.js?v=20261017-forms1'/>"></script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
