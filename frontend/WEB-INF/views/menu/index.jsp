<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container home-menu-page">

    <!-- 1. Hero Section (Image 1) -->
    <section class="crave-hero-banner">
        <div class="hero-text-col">
            <span class="hero-eyebrow">YOUR NEXT FAVORITE MEAL</span>
            <h1 class="hero-heading">Delicious food,<br>delivered to you.</h1>
            <p class="hero-subtext">Discover your favorite meals and drinks and order them in just a few clicks.</p>

            <form action="<c:url value='/menu'/>#catalogSection" method="GET" class="hero-search-pill">
                <div class="search-input-field">
                    <svg class="search-icon-svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                        <circle cx="11" cy="11" r="8"></circle>
                        <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                    <input type="text" name="keyword" value="<c:out value='${keyword}'/>" placeholder="What are you craving?" aria-label="What are you craving?">
                </div>

                <div class="location-badge">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#ef5b35" stroke-width="2.5">
                        <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                        <circle cx="12" cy="10" r="3"></circle>
                    </svg>
                    <span>Downtown</span>
                </div>

                <button type="submit" class="hero-order-btn">Order Now</button>
            </form>
        </div>

        <div class="hero-image-col">
            <div class="hero-photo-wrapper">
                <img src="https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1000&q=80"
                     alt="Delicious dining table flatlay"
                     loading="eager"
                     onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1000&q=80';">
            </div>
        </div>
    </section>

    <!-- 2. Browse by craving (Image 1) -->
    <section class="craving-section">
        <div class="section-header-row">
            <h2 class="section-title">Browse by craving</h2>
            <a href="<c:url value='/menu#catalogSection'/>" class="see-all-link">See all categories</a>
        </div>

        <div class="craving-cards-grid">
            <a href="<c:url value='/menu?categoryId=DM01#catalogSection'/>" class="craving-tile">
                <div class="craving-tile-media">
                    <img src="https://images.unsplash.com/photo-1568901346375-23c9450c58cd?auto=format&fit=crop&w=400&q=80" alt="Burgers">
                </div>
                <span class="craving-tile-name">Burgers</span>
            </a>

            <a href="<c:url value='/menu?categoryId=DM05#catalogSection'/>" class="craving-tile">
                <div class="craving-tile-media">
                    <img src="https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=400&q=80" alt="Pizza">
                </div>
                <span class="craving-tile-name">Pizza</span>
            </a>

            <a href="<c:url value='/menu?categoryId=DM06#catalogSection'/>" class="craving-tile">
                <div class="craving-tile-media">
                    <img src="https://images.unsplash.com/photo-1579871494447-9811cf80d66c?auto=format&fit=crop&w=400&q=80" alt="Sushi">
                </div>
                <span class="craving-tile-name">Sushi</span>
            </a>

            <a href="<c:url value='/menu?categoryId=DM01#catalogSection'/>" class="craving-tile">
                <div class="craving-tile-media">
                    <img src="https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=400&q=80" alt="Noodles">
                </div>
                <span class="craving-tile-name">Noodles</span>
            </a>

            <a href="<c:url value='/menu?categoryId=DM03#catalogSection'/>" class="craving-tile">
                <div class="craving-tile-media">
                    <img src="https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=400&q=80" alt="Desserts">
                </div>
                <span class="craving-tile-name">Desserts</span>
            </a>
        </div>
    </section>

    <!-- 3. Find something delicious / Popular near you (Images 1 & 2) -->
    <section class="catalog-section" id="catalogSection">
        <header class="catalog-header">
            <h2 class="catalog-main-title">Find something delicious</h2>
            <p class="catalog-subtitle">Handpicked favorites from local kitchens, ready when you are.</p>
        </header>

        <!-- Category Text Filter Tabs (Image 2) -->
        <nav class="catalog-tabs-bar" aria-label="Lọc theo danh mục">
            <a href="<c:url value='/menu#catalogSection'/>"
               class="catalog-tab-link ${empty currentCategoryId ? 'active' : ''}">Popular</a>
            <c:forEach var="cat" items="${categories}">
                <a href="<c:url value='/menu?categoryId=${cat.id}#catalogSection'/>"
                   class="catalog-tab-link ${currentCategoryId == cat.id ? 'active' : ''}">${cat.name}</a>
            </c:forEach>
        </nav>

        <c:if test="${empty foods}">
            <div class="empty-state">
                <span>🍽️</span>
                <h3>No dishes found</h3>
                <p>Try searching with another keyword or explore other categories.</p>
                <a href="<c:url value='/menu#catalogSection'/>" class="button primary empty-btn">View all foods</a>
            </div>
        </c:if>

        <c:if test="${not empty foods}">
            <div class="clean-food-grid">
                <c:forEach var="food" items="${foods}">
                    <article class="clean-food-card" onclick="location.href='<c:url value='/menu/detail?id=${food.id}'/>'">
                        <div class="clean-card-image-wrap">
                            <img src="${not empty food.imageUrl ? food.imageUrl : pageContext.request.contextPath.concat('/assets/images/placeholder.jpg')}"
                                 alt="${food.name}"
                                 loading="lazy"
                                 onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=600&q=80';">
                        </div>

                        <div class="clean-card-body">
                            <h3 class="clean-card-title">${food.name}</h3>
                            <p class="clean-card-desc">${not empty food.description ? food.description : 'Fresh, flavorful recipe made with natural local ingredients.'}</p>

                            <div class="clean-card-bottom">
                                <span class="clean-price">
                                    <fmt:formatNumber value="${food.price}" type="number" groupingUsed="true"/> ₫
                                </span>
                                <button type="button" class="heart-fav-btn" onclick="event.stopPropagation(); toggleHeart(this)" aria-label="Yêu thích">
                                    <svg class="heart-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                                    </svg>
                                </button>
                            </div>
                        </div>
                    </article>
                </c:forEach>
            </div>
        </c:if>
    </section>

    <!-- 4. Promo Banner (Image 1) -->
    <section class="crave-promo-banner">
        <div class="promo-text-wrap">
            <span class="promo-badge">NEW TO CRAVE?</span>
            <h2 class="promo-title">20% OFF YOUR FIRST ORDER</h2>
            <p class="promo-subtext">Use code <strong>FIRSTBITE</strong> at checkout. Good food is waiting.</p>
        </div>
        <div class="promo-action-wrap">
            <a href="<c:url value='/menu#catalogSection'/>" class="promo-order-btn">Order Now</a>
        </div>
    </section>

    <!-- 5. Feature Badges (Image 1) -->
    <section class="crave-features-row">
        <div class="feature-card">
            <div class="feature-icon-box">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef5b35" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="1" y="3" width="15" height="13"></rect>
                    <polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon>
                    <circle cx="5.5" cy="18.5" r="2.5"></circle>
                    <circle cx="18.5" cy="18.5" r="2.5"></circle>
                </svg>
            </div>
            <div class="feature-info">
                <h3>Fast delivery</h3>
                <p>Hot and fresh at your door.</p>
            </div>
        </div>

        <div class="feature-card">
            <div class="feature-icon-box">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef5b35" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                </svg>
            </div>
            <div class="feature-info">
                <h3>Fresh food</h3>
                <p>Picked by the places you love.</p>
            </div>
        </div>

        <div class="feature-card">
            <div class="feature-icon-box">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef5b35" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                </svg>
            </div>
            <div class="feature-info">
                <h3>Secure payment</h3>
                <p>Every checkout is protected.</p>
            </div>
        </div>

        <div class="feature-card">
            <div class="feature-icon-box">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#ef5b35" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 18v-6a9 9 0 0 1 18 0v6"></path>
                    <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"></path>
                </svg>
            </div>
            <div class="feature-info">
                <h3>24/7 support</h3>
                <p>A real team, ready to help.</p>
            </div>
        </div>
    </section>

</main>

<script>
function toggleHeart(btn) {
    btn.classList.toggle('active');
    const svg = btn.querySelector('.heart-icon');
    if (btn.classList.contains('active')) {
        svg.setAttribute('fill', '#ef5b35');
        svg.setAttribute('stroke', '#ef5b35');
    } else {
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
    }
}
</script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
