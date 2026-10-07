<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container">
    <div class="product-detail-wrapper" id="productDetail">
        <p class="eyebrow">Chi tiết món ăn: ${food.categoryName}</p>
        <h1 id="foodName">${food.name}</h1>
        <div class="product-image">
            <img src="${not empty food.imageUrl ? food.imageUrl : pageContext.request.contextPath.concat('/assets/images/placeholder.jpg')}" alt="${food.name}" style="max-width: 400px; border-radius: 8px;">
        </div>
        <p class="price" id="foodPrice">Giá cơ bản: ${food.price} VNĐ</p>
        <p class="description" id="foodDesc">${food.description}</p>

        <section class="food-options" id="foodOptions">
            <h3>Tùy chọn</h3>
            <c:if test="${empty food.options}">
                <p>Không có tùy chọn nào cho món này.</p>
            </c:if>
            <c:if test="${not empty food.options}">
                <ul>
                <c:forEach var="option" items="${food.options}">
                    <li>
                        <strong>${option.optionType}</strong>: ${option.name}
                        <span class="price">(+${option.extraPrice} VNĐ)</span>
                    </li>
                </c:forEach>
                </ul>
            </c:if>
        </section>
    </div>
</main>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
