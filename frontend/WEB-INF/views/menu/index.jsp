<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN" />
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container home-menu-page" id="main-content">
    <c:if test="${not empty flashError}"><p class="form-error" role="alert"><c:out value="${flashError}"/></p></c:if>
    <c:if test="${not empty flashMessage}"><p class="form-success" role="status"><c:out value="${flashMessage}"/></p></c:if>
    <section class="crave-hero-banner" aria-labelledby="hero-title">
        <div class="hero-text-col">
            <span class="hero-eyebrow">Bữa ngon tiếp theo của bạn</span>
            <h1 class="hero-heading" id="hero-title">Món ngon mỗi ngày,<br>giao tận tay bạn.</h1>
            <p class="hero-subtext">Khám phá món ăn và thức uống yêu thích. Chọn món, tùy chỉnh theo khẩu vị và đặt hàng chỉ trong vài bước.</p>
            <form action="<c:url value='/menu'/>#catalogSection" method="get" class="hero-search-pill" role="search">
                <div class="search-input-field">
                    <svg class="search-icon-svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/></svg>
                    <input type="search" name="keyword" value="<c:out value='${keyword}'/>" placeholder="Hôm nay bạn muốn ăn gì?" aria-label="Tìm món ăn" maxlength="100">
                    <c:if test="${not empty currentCategoryId}"><input type="hidden" name="categoryId" value="<c:out value='${currentCategoryId}'/>"></c:if>
                </div>
                <button type="submit" class="hero-order-btn">Tìm món</button>
            </form>
        </div>
        <div class="hero-image-col">
            <div class="hero-photo-wrapper">
                <img src="<c:url value='/assets/images/hero-meal.jpg'/>" alt="Bữa ăn với các món ngon được bày trên bàn" loading="eager" fetchpriority="high" onerror="this.onerror=null;this.src='<c:url value='/assets/images/food-placeholder.svg'/>'">
            </div>
        </div>
    </section>

    <c:if test="${not empty categories}">
        <section class="craving-section" aria-labelledby="categories-title">
            <div class="section-header-row">
                <h2 class="section-title" id="categories-title">Chọn theo khẩu vị</h2>
                <a href="#catalogSection" class="see-all-link">Xem thực đơn →</a>
            </div>
            <div class="craving-cards-grid">
                <c:forEach var="cat" items="${categories}">
                    <c:url var="categoryUrl" value="/menu"><c:param name="categoryId" value="${cat.id}"/></c:url>
                    <a href="<c:out value='${categoryUrl}'/>#catalogSection" class="craving-tile ${currentCategoryId == cat.id ? 'active' : ''}">
                        <span class="craving-tile-media category-art" aria-hidden="true" data-category-carousel>
                            <c:choose>
                                <c:when test="${not empty cat.imageUrls}">
                                    <c:forEach var="imgUrl" items="${cat.imageUrls}" varStatus="imgStatus">
                                        <img src="<c:out value='${imgUrl}'/>"
                                             alt="<c:out value='${cat.name}'/>"
                                             class="category-carousel-img ${imgStatus.first ? 'active' : ''}"
                                             loading="lazy">
                                    </c:forEach>
                                </c:when>
                                <c:otherwise>
                                    <svg viewBox="0 0 120 90" fill="none"><ellipse cx="60" cy="48" rx="38" ry="28" fill="currentColor" opacity=".12"/><ellipse cx="60" cy="48" rx="29" ry="20" stroke="currentColor" stroke-width="3"/><path d="M18 22v19m-5-19v13c0 4 10 4 10 0V22M18 40v27M103 23c-8 2-9 19-1 21v23m1-44v21M47 48l9 7 19-17" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                </c:otherwise>
                            </c:choose>
                        </span>
                        <span class="craving-tile-name"><c:out value="${cat.name}"/></span>
                    </a>
                </c:forEach>
            </div>
        </section>
    </c:if>

    <section class="catalog-section" id="catalogSection" aria-labelledby="catalog-title">
        <header class="catalog-header">
            <h2 class="catalog-main-title" id="catalog-title">Tìm món ngon bạn thích</h2>
            <p class="catalog-subtitle">Món ăn được chuẩn bị theo đơn, sẵn sàng cho bữa ngon của bạn.</p>
        </header>
        <nav class="catalog-tabs-bar" aria-label="Lọc theo danh mục">
            <c:url var="allFoodsUrl" value="/menu"><c:param name="keyword" value="${keyword}"/></c:url>
            <a href="<c:out value='${allFoodsUrl}'/>#catalogSection" class="catalog-tab-link ${empty currentCategoryId ? 'active' : ''}" ${empty currentCategoryId ? 'aria-current="page"' : ''}>Tất cả món</a>
            <c:forEach var="cat" items="${categories}">
                <c:url var="filterUrl" value="/menu"><c:param name="categoryId" value="${cat.id}"/><c:param name="keyword" value="${keyword}"/></c:url>
                <a href="<c:out value='${filterUrl}'/>#catalogSection" class="catalog-tab-link ${currentCategoryId == cat.id ? 'active' : ''}" ${currentCategoryId == cat.id ? 'aria-current="page"' : ''}><c:out value="${cat.name}"/></a>
            </c:forEach>
        </nav>
        <c:if test="${not empty keyword}">
            <p class="search-results-label">Kết quả tìm kiếm cho “<strong><c:out value="${keyword}"/></strong>” · <a href="<c:url value='/menu'/>#catalogSection">Xóa bộ lọc</a></p>
        </c:if>
        <c:if test="${empty foods}">
            <div class="empty-state">
                <span class="empty-icon-art" aria-hidden="true">🍽️</span>
                <h3 class="empty-title">Chưa tìm thấy món phù hợp</h3>
                <p class="empty-desc">Thử một từ khóa khác hoặc xem toàn bộ thực đơn để chọn món bạn thích.</p>
                <a href="<c:url value='/menu'/>#catalogSection" class="button primary empty-btn">Xem tất cả món</a>
            </div>
        </c:if>
        <div class="clean-food-grid">
            <c:forEach var="food" items="${foods}">
                <c:url var="foodUrl" value="/menu/detail"><c:param name="id" value="${food.id}"/></c:url>
                <article class="clean-food-card ${food.status == 'UNAVAILABLE' ? 'food-card-unavailable' : ''}">
                    <a href="<c:out value='${foodUrl}'/>" class="clean-card-image-wrap" aria-label="Xem món <c:out value='${food.name}'/>">
                        <img src="<c:out value='${not empty food.imageUrl ? food.imageUrl : pageContext.request.contextPath.concat("/assets/images/food-placeholder.svg")}'/>" alt="<c:out value='${food.name}'/>" loading="lazy" onerror="this.onerror=null;this.src='<c:url value='/assets/images/food-placeholder.svg'/>'">
                        <c:if test="${food.status == 'UNAVAILABLE'}"><span class="card-status-badge badge-unavailable">Tạm hết</span></c:if>
                    </a>
                    <div class="clean-card-body">
                        <h3 class="clean-card-title"><a href="<c:out value='${foodUrl}'/>"><c:out value="${food.name}"/></a></h3>
                        <p class="clean-card-desc"><c:out value="${not empty food.description ? food.description : 'Món ngon được chuẩn bị riêng cho bữa ăn của bạn.'}"/></p>
                        <div class="clean-card-bottom">
                            <span class="clean-price"><fmt:formatNumber value="${food.price}" type="number" groupingUsed="true"/> ₫</span>
                            <c:choose>
                                <c:when test="${food.status == 'UNAVAILABLE'}">
                                    <button type="button" class="food-card-action" disabled aria-label="Món <c:out value='${food.name}'/> tạm hết" title="Món tạm hết">
                                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M18 6 6 18M6 6l12 12"/></svg>
                                    </button>
                                </c:when>
                                <c:otherwise>
                                    <c:url var="returnMenu" value="/menu"><c:param name="categoryId" value="${currentCategoryId}"/><c:param name="keyword" value="${keyword}"/></c:url>
                                    <form method="post" action="<c:url value='/cart/items'/>">
                                        <input type="hidden" name="foodId" value="<c:out value='${food.id}'/>">
                                        <input type="hidden" name="quantity" value="1">
                                        <input type="hidden" name="action" value="add">
                                        <input type="hidden" name="returnTo" value="<c:out value='${returnMenu}'/>">
                                        <button type="submit" class="food-card-action" aria-label="Thêm nhanh <c:out value='${food.name}'/> vào giỏ hàng" title="Thêm nhanh vào giỏ hàng">
                                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
                                        </button>
                                    </form>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </article>
            </c:forEach>
        </div>
    </section>

    <section class="crave-promo-banner" aria-labelledby="promo-title">
        <div class="promo-text-wrap">
            <span class="promo-badge">Ăn ngon, thêm ưu đãi</span>
            <h2 class="promo-title" id="promo-title">Khám phá khuyến mãi hôm nay</h2>
            <p class="promo-subtext">Xem mã đang có và điều kiện áp dụng trước khi thanh toán.</p>
        </div>
        <a href="<c:url value='/promotions'/>" class="promo-order-btn">Xem ưu đãi →</a>
    </section>
    <section class="crave-features-row" aria-label="Đặt món cùng Crave">
        <div class="feature-card"><span class="feature-symbol" aria-hidden="true">↗</span><div class="feature-info"><h3>Nhận món theo ý bạn</h3><p>Giao tận nơi hoặc lấy tại quán.</p></div></div>
        <div class="feature-card"><span class="feature-symbol" aria-hidden="true">✧</span><div class="feature-info"><h3>Tùy chỉnh khẩu vị</h3><p>Chọn kích cỡ và món thêm phù hợp.</p></div></div>
        <div class="feature-card"><span class="feature-symbol" aria-hidden="true">✓</span><div class="feature-info"><h3>Chi phí rõ ràng</h3><p>Xem tổng tiền trước khi đặt hàng.</p></div></div>
        <div class="feature-card"><span class="feature-symbol" aria-hidden="true">◷</span><div class="feature-info"><h3>Theo dõi đơn hàng</h3><p>Cập nhật trạng thái trong tài khoản.</p></div></div>
    </section>
</main>
<%@ include file="/WEB-INF/views/components/footer.jspf" %>
