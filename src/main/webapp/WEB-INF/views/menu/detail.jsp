<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>
<input type="hidden" id="pageCartCount" value="<c:out value='${sessionScope.cartItemCount}'/>">
<div id="cartFlash" hidden data-message="<c:out value='${sessionScope.cartMessage}'/>" data-type="<c:out value='${sessionScope.cartMessageType}'/>"></div>
<c:remove var="cartMessage" scope="session"/><c:remove var="cartMessageType" scope="session"/>

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

                <form id="orderCustomizationForm" class="customization-form" method="post" action="<c:url value='/cart/action'/>" data-food-id="<c:out value='${food.id}'/>" onsubmit="event.preventDefault(); handleAddToCart();">
                    <input type="hidden" name="action" value="add">
                    <input type="hidden" name="foodId" value="<c:out value='${food.id}'/>">
                    <input type="hidden" name="returnTo" value="/menu/detail?id=<c:out value='${food.id}'/>">
                    <input type="hidden" name="note" value="">
                    <c:if test="${not empty food.options}">
                        <section class="options-container" id="foodOptionsContainer">
                            <h3 class="options-title">Tùy chọn cho món ăn</h3>

                        <c:set var="hasSize" value="false" />
                        <c:forEach var="opt" items="${food.options}">
                            <c:if test="${opt.optionType == 'SIZE'}"><c:set var="hasSize" value="true" /></c:if>
                        </c:forEach>
                        <c:if test="${hasSize}">
                            <fieldset class="option-group">
                                <legend class="option-group-title">Kích cỡ</legend>
                                <div class="option-items-grid">
                                    <label class="option-card">
                                        <input type="radio" name="option_size" value="0" data-extra="0" checked onchange="updateTotalPrice()">
                                        <span class="option-card-inner">
                                            <span class="opt-label">Tiêu chuẩn</span>
                                            <span class="opt-price">+0 ₫</span>
                                        </span>
                                    </label>
                                    <c:forEach var="opt" items="${food.options}">
                                        <c:if test="${opt.optionType == 'SIZE'}">
                                            <label class="option-card">
                                                <input type="radio" name="option_size" value="<c:out value='${opt.id}'/>" data-extra="<c:out value='${opt.extraPrice}'/>" onchange="updateTotalPrice()">
                                                <span class="option-card-inner">
                                                    <span class="opt-label"><c:out value="${opt.name}"/></span>
                                                    <span class="opt-price">+<fmt:formatNumber value="${opt.extraPrice}" type="number" groupingUsed="true"/> ₫</span>
                                                </span>
                                            </label>
                                        </c:if>
                                    </c:forEach>
                                </div>
                            </fieldset>
                        </c:if>

                        <c:set var="hasTopping" value="false" />
                        <c:forEach var="opt" items="${food.options}">
                            <c:if test="${opt.optionType == 'TOPPING'}"><c:set var="hasTopping" value="true" /></c:if>
                        </c:forEach>
                        <c:if test="${hasTopping}">
                            <fieldset class="option-group">
                                <legend class="option-group-title">Món thêm</legend>
                                <div class="option-items-grid">
                                    <c:forEach var="opt" items="${food.options}">
                                        <c:if test="${opt.optionType == 'TOPPING'}">
                                            <label class="option-card">
                                                <input type="checkbox" name="option_topping" value="<c:out value='${opt.id}'/>" data-extra="<c:out value='${opt.extraPrice}'/>" onchange="updateTotalPrice()">
                                                <span class="option-card-inner">
                                                    <span class="opt-label"><c:out value="${opt.name}"/></span>
                                                    <span class="opt-price">+<fmt:formatNumber value="${opt.extraPrice}" type="number" groupingUsed="true"/> ₫</span>
                                                </span>
                                            </label>
                                        </c:if>
                                    </c:forEach>
                                </div>
                            </fieldset>
                        </c:if>

                        <c:set var="hasSugar" value="false" />
                        <c:forEach var="opt" items="${food.options}">
                            <c:if test="${opt.optionType == 'SUGAR_LEVEL'}"><c:set var="hasSugar" value="true" /></c:if>
                        </c:forEach>
                        <c:if test="${hasSugar}">
                            <fieldset class="option-group">
                                <legend class="option-group-title">Mức đường</legend>
                                <div class="option-items-grid">
                                    <c:forEach var="opt" items="${food.options}" varStatus="status">
                                        <c:if test="${opt.optionType == 'SUGAR_LEVEL'}">
                                            <label class="option-card">
                                                <input type="radio" name="option_sugar" value="<c:out value='${opt.id}'/>" data-extra="<c:out value='${opt.extraPrice}'/>" ${status.first ? 'checked' : ''} onchange="updateTotalPrice()">
                                                <span class="option-card-inner">
                                                    <span class="opt-label"><c:out value="${opt.name}"/></span>
                                                    <span class="opt-price">+<fmt:formatNumber value="${opt.extraPrice}" type="number" groupingUsed="true"/> ₫</span>
                                                </span>
                                            </label>
                                        </c:if>
                                    </c:forEach>
                                </div>
                            </fieldset>
                        </c:if>

                        <c:set var="hasIce" value="false" />
                        <c:forEach var="opt" items="${food.options}">
                            <c:if test="${opt.optionType == 'ICE_LEVEL'}"><c:set var="hasIce" value="true" /></c:if>
                        </c:forEach>
                        <c:if test="${hasIce}">
                            <fieldset class="option-group">
                                <legend class="option-group-title">Mức đá</legend>
                                <div class="option-items-grid">
                                    <c:forEach var="opt" items="${food.options}" varStatus="status">
                                        <c:if test="${opt.optionType == 'ICE_LEVEL'}">
                                            <label class="option-card">
                                                <input type="radio" name="option_ice" value="<c:out value='${opt.id}'/>" data-extra="<c:out value='${opt.extraPrice}'/>" ${status.first ? 'checked' : ''} onchange="updateTotalPrice()">
                                                <span class="option-card-inner">
                                                    <span class="opt-label"><c:out value="${opt.name}"/></span>
                                                    <span class="opt-price">+<fmt:formatNumber value="${opt.extraPrice}" type="number" groupingUsed="true"/> ₫</span>
                                                </span>
                                            </label>
                                        </c:if>
                                    </c:forEach>
                                </div>
                            </fieldset>
                        </c:if>

                        <c:set var="hasOther" value="false" />
                        <c:forEach var="opt" items="${food.options}">
                            <c:if test="${opt.optionType == 'OTHER'}"><c:set var="hasOther" value="true" /></c:if>
                        </c:forEach>
                        <c:if test="${hasOther}">
                            <fieldset class="option-group">
                                <legend class="option-group-title">Tùy chọn khác</legend>
                                <div class="option-items-grid">
                                    <c:forEach var="opt" items="${food.options}">
                                        <c:if test="${opt.optionType == 'OTHER'}">
                                            <label class="option-card">
                                                <input type="checkbox" name="option_other" value="<c:out value='${opt.id}'/>" data-extra="<c:out value='${opt.extraPrice}'/>" onchange="updateTotalPrice()">
                                                <span class="option-card-inner">
                                                    <span class="opt-label"><c:out value="${opt.name}"/></span>
                                                    <span class="opt-price">+<fmt:formatNumber value="${opt.extraPrice}" type="number" groupingUsed="true"/> ₫</span>
                                                </span>
                                            </label>
                                        </c:if>
                                    </c:forEach>
                                </div>
                            </fieldset>
                        </c:if>
                    </section>
                </c:if>

                <div class="order-action-bar">
                    <div class="quantity-picker-wrap">
                        <label for="orderQuantity" class="qty-label">Số lượng:</label>
                        <div class="qty-controls">
                            <button type="button" class="qty-btn" onclick="changeQuantity(-1)" aria-label="Giảm">-</button>
                            <input type="number" id="orderQuantity" name="quantity" value="1" min="1" max="99" onchange="updateTotalPrice()">
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

<script src="<c:url value='/assets/js/menu-detail.js?v=20261017-forms1'/>"></script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
