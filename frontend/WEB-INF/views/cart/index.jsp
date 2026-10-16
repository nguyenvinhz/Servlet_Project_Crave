<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="pageTitle" value="Giỏ hàng của bạn"/>
<c:set var="pageStyle" value="cart"/>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main id="main-content" class="cart-page-wrapper">
    <div class="cart-container">
        <header class="cart-header-section">
            <h1 class="cart-page-title">Giỏ hàng của bạn</h1>
            <p class="cart-subtitle">Quản lý các món ăn đã chọn và áp dụng mã giảm giá tốt nhất.</p>
        </header>
        <c:if test="${not empty flashError}"><p class="voucher-feedback error" role="alert"><c:out value="${flashError}"/></p></c:if>
        <c:if test="${not empty flashMessage}"><p class="voucher-feedback success" role="status"><c:out value="${flashMessage}"/></p></c:if>
        <c:if test="${not empty editItem}">
            <section class="order-summary-card cart-item-editor" id="cartItemEditor" aria-labelledby="cartEditorTitle">
                <h2 class="order-summary-title" id="cartEditorTitle">Chỉnh sửa <c:out value="${editItem.foodName}"/></h2>
                <form id="cartItemEditorForm" action="<c:url value='/cart/items'/>" method="post" data-base-price="<c:out value='${editFood.price}'/>">
                    <input type="hidden" name="action" value="update">
                    <input type="hidden" name="cartItemId" value="<c:out value='${editItem.cartItemId}'/>">
                    <input type="hidden" name="customization" value="true">
                    <%@ include file="/WEB-INF/views/components/food-options.jspf" %>
                    <label class="cart-editor-field" for="cartEditorQuantity">Số lượng
                        <input id="cartEditorQuantity" name="quantity" type="number" value="${editItem.quantity}" min="1" max="100" step="1" required>
                    </label>
                    <label class="cart-editor-field" for="cartEditorNote">Ghi chú cho món
                        <textarea id="cartEditorNote" name="note" maxlength="255" rows="2"><c:out value="${editItem.note}"/></textarea>
                    </label>
                    <div class="summary-total-row"><span>Thành tiền</span><strong id="cartEditorTotal"><fmt:formatNumber value="${editItem.lineTotal}" groupingUsed="true"/> ₫</strong></div>
                    <div class="cart-editor-actions">
                        <a href="<c:url value='/cart'/>" class="voucher-apply-btn">Hủy</a>
                        <button type="submit" class="btn-checkout" ${editFood.status != 'AVAILABLE' ? 'disabled' : ''}>Lưu thay đổi</button>
                    </div>
                    <c:if test="${editFood.status != 'AVAILABLE'}"><p role="alert">Món ăn hiện tạm ngưng phục vụ. Bạn có thể xóa món khỏi giỏ hàng.</p></c:if>
                </form>
            </section>
        </c:if>
        <c:choose>
            <c:when test="${empty cart.items}">
                <div class="cart-empty-state">
                    <div class="cart-empty-illustration" aria-hidden="true">🛒</div>
                    <h2 class="cart-empty-title">Giỏ hàng của bạn đang trống</h2>
                    <p class="cart-empty-desc">Bạn chưa thêm món ăn nào vào giỏ. Hãy khám phá thực đơn để tìm món ngon yêu thích.</p>
                    <a href="<c:url value='/menu'/>" class="btn-explore-menu">Khám phá thực đơn ngay</a>
                </div>
            </c:when>
            <c:otherwise>
                <div class="cart-layout-grid">
                    <div class="cart-items-column">
                        <div class="cart-items-column">
                            <c:forEach var="item" items="${cart.items}">
                                <c:url var="editUrl" value="/cart"><c:param name="edit" value="${item.cartItemId}"/></c:url>
                                <article class="cart-item-card">
                                    <div class="cart-item-image-wrapper">
                                        <c:set var="cartImageUrl" value="${empty item.imageUrl ? pageContext.request.contextPath.concat('/assets/images/food-placeholder.svg') : item.imageUrl}"/>
                                        <c:if test="${not empty item.imageUrl and not item.imageUrl.startsWith('/') and not item.imageUrl.startsWith('http://') and not item.imageUrl.startsWith('https://')}">
                                            <c:set var="cartImageUrl" value="${pageContext.request.contextPath.concat('/').concat(item.imageUrl)}"/>
                                        </c:if>
                                        <img class="cart-item-image" src="<c:out value='${cartImageUrl}'/>" alt="<c:out value='${item.foodName}'/>" onerror="this.onerror=null;this.src='<c:url value='/assets/images/food-placeholder.svg'/>'">
                                    </div>
                                    <div class="cart-item-info">
                                        <h3 class="cart-item-title"><a class="cart-item-title-button" href="<c:out value='${editUrl}'/>#cartItemEditor"><c:out value="${item.foodName}"/></a></h3>
                                        <c:if test="${not empty item.options}"><p class="cart-item-options"><c:forEach var="opt" items="${item.options}" varStatus="loop"><c:if test="${not loop.first}"> · </c:if><c:out value="${opt.name}"/></c:forEach></p></c:if>
                                        <c:if test="${not empty item.note}"><p class="cart-item-note">Ghi chú: <c:out value="${item.note}"/></p></c:if>
                                        <div class="cart-item-actions">
                                            <a class="cart-item-edit-btn" href="<c:out value='${editUrl}'/>#cartItemEditor">Chỉnh sửa món</a>
                                            <form action="<c:url value='/cart/items'/>" method="post" class="cart-stepper">
                                                <input type="hidden" name="action" value="update">
                                                <input type="hidden" name="cartItemId" value="<c:out value='${item.cartItemId}'/>">
                                                <button type="submit" class="cart-stepper-btn" name="quantity" value="${item.quantity - 1}" aria-label="Giảm số lượng">−</button>
                                                <span class="cart-stepper-val">${item.quantity}</span>
                                                <button type="submit" class="cart-stepper-btn" name="quantity" value="${item.quantity + 1}" ${item.quantity >= 100 ? 'disabled' : ''} aria-label="Tăng số lượng">+</button>
                                            </form>
                                            <form action="<c:url value='/cart/items'/>" method="post">
                                                <input type="hidden" name="action" value="remove">
                                                <input type="hidden" name="cartItemId" value="<c:out value='${item.cartItemId}'/>">
                                                <button type="submit" class="cart-item-remove-btn" title="Xóa món khỏi giỏ">Xóa</button>
                                            </form>
                                        </div>
                                    </div>
                                    <div class="cart-item-price-tag"><fmt:formatNumber value="${item.unitPrice}" groupingUsed="true"/> ₫ × ${item.quantity}</div>
                                </article>
                            </c:forEach>
                        </div>
                        <div class="cart-bottom-actions">
                            <a href="<c:url value='/menu'/>" class="btn-link-action">← Chọn thêm món khác</a>
                            <form action="<c:url value='/cart/clear'/>" method="post" data-confirm="Xóa toàn bộ giỏ hàng?">
                                <button type="submit" class="btn-link-action danger">Xóa toàn bộ giỏ</button>
                            </form>
                        </div>
                    </div>
                    <div class="cart-summary-column">
                        <div class="order-summary-card">
                            <h2 class="order-summary-title">Tóm tắt đơn hàng</h2>
                            <div class="summary-row"><span>Tạm tính</span><span><fmt:formatNumber value="${cart.subtotal}" groupingUsed="true"/> ₫</span></div>
                            <div class="summary-row"><span>Phí giao hàng</span><span>Tính khi thanh toán</span></div>
                            <c:if test="${discountAmount > 0}"><div class="summary-row discount-row"><span>Giảm giá</span><span>−<fmt:formatNumber value="${discountAmount}" groupingUsed="true"/> ₫</span></div></c:if>
                            <div class="summary-divider"></div>
                            <div class="summary-total-row"><span class="summary-total-label">Tiền món sau giảm</span><span class="summary-total-value"><fmt:formatNumber value="${finalSubtotal}" groupingUsed="true"/> ₫</span></div>
                            <p class="cart-shipping-hint">Phí giao hàng được cập nhật sau khi chọn điểm nhận ở bước thanh toán.</p>
                            <div class="voucher-section">
                                <div class="voucher-section-title"><span>Mã khuyến mãi</span><a href="<c:url value='/promotions'/>" class="voucher-view-all-link">Xem mã có sẵn</a></div>
                                <c:choose>
                                    <c:when test="${not empty appliedPromotion}">
                                        <form class="voucher-applied-chip" action="<c:url value='/cart/promotion'/>" method="post">
                                            <input type="hidden" name="action" value="remove">
                                            <div class="voucher-chip-info"><span class="voucher-code-badge"><c:out value="${appliedPromotion.code}"/></span><span class="voucher-discount-text">−<fmt:formatNumber value="${discountAmount}" groupingUsed="true"/> ₫</span></div>
                                            <button type="submit" class="voucher-remove-btn" aria-label="Hủy mã khuyến mãi">×</button>
                                        </form>
                                    </c:when>
                                    <c:otherwise>
                                        <form class="voucher-input-group" action="<c:url value='/cart/promotion'/>" method="post">
                                            <input type="text" name="code" value="<c:out value='${voucherCode}'/>" class="voucher-input" placeholder="Nhập mã ưu đãi" aria-label="Mã khuyến mãi" maxlength="50" required autocomplete="off">
                                            <button type="submit" class="voucher-apply-btn">Áp dụng</button>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                            <a href="<c:url value='/checkout'/>" class="btn-checkout">Tiếp tục thanh toán</a>
                        </div>
                    </div>
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</main>
<script src="<c:url value='/assets/js/cart.js'/>"></script>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
