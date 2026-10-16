<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main id="main-content" class="page-container product-detail-page">
    <c:if test="${empty food}">
        <div class="empty-state not-found-detail">
            <span class="empty-icon-art">🍽️</span>
            <h2 class="empty-title">Không tìm thấy món ăn</h2>
            <p class="empty-desc">Món ăn bạn đang tìm kiếm không tồn tại hoặc đã được gỡ bỏ khỏi thực đơn.</p>
            <div class="empty-actions">
                <a href="<c:url value='/menu'/>" class="button primary empty-btn">Quay lại thực đơn</a>
            </div>
        </div>
    </c:if>

    <c:if test="${not empty food}">
        <nav class="breadcrumb-nav" aria-label="Đường dẫn trang">
            <a href="<c:url value='/menu'/>">Thực đơn</a>
            <span class="sep">/</span>
            <a href="<c:url value='/menu'><c:param name='categoryId' value='${food.categoryId}'/></c:url>"><c:out value="${food.categoryName}"/></a>
            <span class="sep">/</span>
            <span class="current"><c:out value="${food.name}"/></span>
        </nav>

        <div class="product-detail-layout" id="productDetail">
            <div class="product-media-panel">
                <div class="product-image-container">
                    <img id="foodImage"
                         src="<c:out value='${not empty food.imageUrl ? food.imageUrl : pageContext.request.contextPath.concat("/assets/images/food-placeholder.svg")}'/>"
                         alt="<c:out value='${food.name}'/>"
                         onerror="this.onerror=null;this.src='<c:url value='/assets/images/food-placeholder.svg'/>';">
                    <div class="product-media-badges">
                        <span class="category-pill"><c:out value="${food.categoryName}"/></span>
                        <span class="status-pill ${food.status == 'AVAILABLE' ? 'available' : 'unavailable'}">
                            ${food.status == 'AVAILABLE' ? 'Đang phục vụ' : 'Tạm hết món'}
                        </span>
                    </div>
                </div>
            </div>

            <div class="product-info-panel">
                <header class="product-heading">
                    <p class="eyebrow"><c:out value="${food.categoryName}"/></p>
                    <h1 id="foodName" class="food-heading-title"><c:out value="${food.name}"/></h1>
                    <div class="price-hero">
                        <span class="price-amount" id="basePriceDisplay" data-base-price="<c:out value='${food.price}'/>">
                            <fmt:formatNumber value="${food.price}" type="number" groupingUsed="true"/>
                        </span>
                        <span class="price-curr">₫</span>
                        <span class="price-note">Giá cơ bản</span>
                    </div>
                </header>

                <c:if test="${food.status == 'UNAVAILABLE'}">
                    <div class="unavailable-notice-banner" role="alert">
                        <span class="notice-icon">⚠️</span>
                        <div class="notice-text">
                            <strong>Món ăn hiện đang tạm ngưng phục vụ</strong>
                            <p>Món này hiện đang tạm hết nguyên liệu hoặc đã ngưng bán hôm nay. Quý khách vui lòng chọn món khác trong thực đơn.</p>
                        </div>
                    </div>
                </c:if>

                <div class="product-description-box">
                    <h3>Mô tả món ăn</h3>
                    <p id="foodDesc"><c:out value="${not empty food.description ? food.description : 'Món ăn thơm ngon được chế biến từ những nguyên liệu tươi mới nhất mỗi ngày.'}"/></p>
                </div>

                <form id="orderCustomizationForm" class="customization-form" action="<c:url value='/cart/items'/>" method="post">
                    <input type="hidden" name="foodId" value="<c:out value='${food.id}'/>">
                    <input type="hidden" name="action" value="add">
                    <%@ include file="/WEB-INF/views/components/food-options.jspf" %>
                    <label class="cart-editor-field" for="orderNote">Ghi chú cho món
                        <textarea id="orderNote" name="note" maxlength="255" rows="2" placeholder="Ví dụ: ít cay, không hành"></textarea>
                    </label>
                <div class="order-action-bar">
                    <div class="quantity-picker-wrap">
                        <label for="orderQuantity" class="qty-label">Số lượng:</label>
                        <div class="qty-controls">
                            <button type="button" class="qty-btn" onclick="changeQuantity(-1)" aria-label="Giảm">-</button>
                            <input type="number" id="orderQuantity" name="quantity" value="1" min="1" max="100" required onchange="updateTotalPrice()">
                            <button type="button" class="qty-btn" onclick="changeQuantity(1)" aria-label="Tăng">+</button>
                        </div>
                    </div>

                    <div class="checkout-summary">
                        <span class="total-label">Tổng cộng:</span>
                        <span class="total-val" id="totalPriceDisplay"><fmt:formatNumber value="${food.price}" type="number" groupingUsed="true"/> ₫</span>
                    </div>

                    <div class="submit-action-wrap">
                        <button type="submit" class="button primary add-cart-btn" id="addToCartBtn" ${food.status == 'UNAVAILABLE' ? 'disabled' : ''}>
                            <span class="cart-icon">🛒</span>
                            <span>${food.status == 'AVAILABLE' ? 'Thêm vào giỏ hàng' : 'Món hiện tạm hết'}</span>
                        </button>
                    </div>
                </div>
            </form>
        </div>
    </div>
    </c:if>
</main>

<script src="<c:url value='/assets/js/menu-detail.js'/>"></script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
