<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container product-detail-page">
    <nav class="breadcrumb-nav" aria-label="Đường dẫn trang">
        <a href="<c:url value='/menu'/>">Thực đơn</a>
        <span class="sep">/</span>
        <a href="<c:url value='/menu?categoryId=${food.categoryId}'/>">${food.categoryName}</a>
        <span class="sep">/</span>
        <span class="current">${food.name}</span>
    </nav>

    <div class="product-detail-layout" id="productDetail">
        <div class="product-media-panel">
            <div class="product-image-container">
                <img id="foodImage"
                     src="${not empty food.imageUrl ? food.imageUrl : pageContext.request.contextPath.concat('/assets/images/placeholder.jpg')}"
                     alt="${food.name}"
                     onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=80';">
                <div class="product-media-badges">
                    <span class="category-pill">${food.categoryName}</span>
                    <span class="status-pill ${food.status == 'AVAILABLE' ? 'available' : 'unavailable'}">
                        ${food.status == 'AVAILABLE' ? 'Đang phục vụ' : 'Tạm hết món'}
                    </span>
                </div>
            </div>
        </div>

        <div class="product-info-panel">
            <header class="product-heading">
                <p class="eyebrow">${food.categoryName}</p>
                <h1 id="foodName" class="food-heading-title">${food.name}</h1>
                <div class="price-hero">
                    <span class="price-amount" id="basePriceDisplay" data-base-price="${food.price}">
                        <fmt:formatNumber value="${food.price}" type="number" groupingUsed="true"/>
                    </span>
                    <span class="price-curr">₫</span>
                    <span class="price-note">Giá cơ bản</span>
                </div>
            </header>

            <div class="product-description-box">
                <h3>Mô tả món ăn</h3>
                <p id="foodDesc">${not empty food.description ? food.description : 'Món ăn thơm ngon được chế biến từ những nguyên liệu tươi mới nhất mỗi ngày.'}</p>
            </div>

            <form id="orderCustomizationForm" class="customization-form" onsubmit="event.preventDefault(); handleAddToCart();">
                <c:if test="${not empty food.options}">
                    <section class="options-container" id="foodOptionsContainer">
                        <h3 class="options-title">Tùy chọn & Thêm món</h3>

                        <c:set var="hasSize" value="false" />
                        <c:forEach var="opt" items="${food.options}">
                            <c:if test="${opt.optionType == 'SIZE'}"><c:set var="hasSize" value="true" /></c:if>
                        </c:forEach>
                        <c:if test="${hasSize}">
                            <fieldset class="option-group">
                                <legend class="option-group-title">Kích cỡ (Size)</legend>
                                <div class="option-items-grid">
                                    <label class="option-card">
                                        <input type="radio" name="option_size" value="0" data-extra="0" checked onchange="updateTotalPrice()">
                                        <span class="option-card-inner">
                                            <span class="opt-label">Tiêu chuẩn (M)</span>
                                            <span class="opt-price">+0 ₫</span>
                                        </span>
                                    </label>
                                    <c:forEach var="opt" items="${food.options}">
                                        <c:if test="${opt.optionType == 'SIZE'}">
                                            <label class="option-card">
                                                <input type="radio" name="option_size" value="${opt.id}" data-extra="${opt.extraPrice}" onchange="updateTotalPrice()">
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
                                <legend class="option-group-title">Món thêm (Topping)</legend>
                                <div class="option-items-grid">
                                    <c:forEach var="opt" items="${food.options}">
                                        <c:if test="${opt.optionType == 'TOPPING'}">
                                            <label class="option-card">
                                                <input type="checkbox" name="option_topping" value="${opt.id}" data-extra="${opt.extraPrice}" onchange="updateTotalPrice()">
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
                                                <input type="radio" name="option_sugar" value="${opt.id}" data-extra="${opt.extraPrice}" ${status.first ? 'checked' : ''} onchange="updateTotalPrice()">
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
                                                <input type="radio" name="option_ice" value="${opt.id}" data-extra="${opt.extraPrice}" ${status.first ? 'checked' : ''} onchange="updateTotalPrice()">
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
                                                <input type="checkbox" name="option_other" value="${opt.id}" data-extra="${opt.extraPrice}" onchange="updateTotalPrice()">
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
</main>

<script>
function changeQuantity(delta) {
    const qtyInput = document.getElementById('orderQuantity');
    let current = parseInt(qtyInput.value) || 1;
    current = Math.max(1, current + delta);
    qtyInput.value = current;
    updateTotalPrice();
}

function updateTotalPrice() {
    const basePriceElem = document.getElementById('basePriceDisplay');
    if (!basePriceElem) return;
    const basePrice = parseFloat(basePriceElem.dataset.basePrice) || 0;

    let extraTotal = 0;
    // Radio & Checkbox options
    const checkedOptions = document.querySelectorAll('#orderCustomizationForm input:checked');
    checkedOptions.forEach(opt => {
        const extra = parseFloat(opt.dataset.extra) || 0;
        extraTotal += extra;
    });

    const qty = parseInt(document.getElementById('orderQuantity').value) || 1;
    const singleItemPrice = basePrice + extraTotal;
    const grandTotal = singleItemPrice * qty;

    const totalDisplay = document.getElementById('totalPriceDisplay');
    if (totalDisplay) {
        totalDisplay.textContent = grandTotal.toLocaleString('vi-VN') + ' ₫';
    }
}

async function handleAddToCart() {
    const btn = document.getElementById('addToCartBtn');
    const originalText = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span>⏳ Đang thêm vào giỏ...</span>';

    const foodId = '${food.id}';
    const quantity = parseInt(document.getElementById('orderQuantity').value) || 1;
    const optionIds = [];
    document.querySelectorAll('#orderCustomizationForm input:checked').forEach(opt => {
        if (opt.value && opt.value !== '0') {
            optionIds.push(opt.value);
        }
    });

    const ctx = typeof window.CONTEXT_PATH === 'string' ? window.CONTEXT_PATH : '';
    const cartUrl = ctx + '/api/cart/items';

    try {
        const response = await fetch(cartUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify({ foodId, quantity, optionIds, note: '' })
        });

        if (response.status === 401) {
            btn.innerHTML = '<span>⚠️ Vui lòng đăng nhập</span>';
            btn.style.background = '#dc2626';
            setTimeout(() => {
                window.location.href = ctx + '/auth/login?returnTo=' + encodeURIComponent(window.location.pathname + window.location.search);
            }, 800);
            return;
        }

        const data = await response.json();
        if (response.ok && data.success) {
            btn.innerHTML = '<span>✅ Đã thêm món vào giỏ!</span>';
            btn.style.background = '#18794e';
            const badge = document.querySelector('.cart-badge-count');
            if (badge) {
                let currentCount = parseInt(badge.textContent) || 0;
                badge.textContent = currentCount + quantity;
                badge.style.display = 'inline-flex';
            }
            const dot = document.querySelector('.cart-badge-dot');
            if (dot) dot.style.display = 'block';
            setTimeout(() => {
                btn.innerHTML = originalText;
                btn.style.background = '';
                btn.disabled = false;
            }, 2000);
        } else {
            const msg = data.error?.message || data.message || 'Không thể thêm vào giỏ hàng.';
            alert(msg);
            btn.innerHTML = originalText;
            btn.style.background = '';
            btn.disabled = false;
        }
    } catch (e) {
        alert('Lỗi kết nối khi thêm giỏ hàng: ' + e.message);
        btn.innerHTML = originalText;
        btn.style.background = '';
        btn.disabled = false;
    }
}
</script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
