<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container">
    <header class="page-heading">
        <p class="eyebrow">Thực đơn hôm nay</p>
        <h1>Món ngon dành cho bạn</h1>
        <p class="lead">Khám phá các món ăn hấp dẫn, đồ uống tươi mát và món tráng miệng ngọt ngào.</p>
    </header>

    <div class="menu-categories" id="categoryTabs">
        <!-- Render danh mục -->
    </div>

    <section class="menu-grid" id="foodGrid">
        <!-- Danh sách món ăn -->
    </section>
</main>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
