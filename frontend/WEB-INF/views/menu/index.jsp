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
        <a href="${pageContext.request.contextPath}/menu" class="btn ${empty currentCategoryId ? 'btn-primary' : 'btn-outline'}">Tất cả</a>
        <c:forEach var="category" items="${categories}">
            <a href="${pageContext.request.contextPath}/menu?categoryId=${category.id}" class="btn ${currentCategoryId == category.id ? 'btn-primary' : 'btn-outline'}">${category.name}</a>
        </c:forEach>
    </div>

    <section class="menu-grid" id="foodGrid">
        <c:forEach var="food" items="${foods}">
            <div class="food-card">
                <img src="${not empty food.imageUrl ? food.imageUrl : pageContext.request.contextPath.concat('/assets/images/placeholder.jpg')}" alt="${food.name}">
                <h3>${food.name}</h3>
                <p class="price">${food.price} VNĐ</p>
                <a href="${pageContext.request.contextPath}/menu/detail?id=${food.id}" class="btn btn-primary">Chi tiết</a>
            </div>
        </c:forEach>
    </section>
</main>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
