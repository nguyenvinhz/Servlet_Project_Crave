<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container">
    <div class="product-detail-wrapper" id="productDetail">
        <p class="eyebrow">Chi tiết món ăn</p>
        <h1 id="foodName">Đang tải thông tin món ăn...</h1>
        <p class="price" id="foodPrice"></p>
        <p class="description" id="foodDesc"></p>

        <section class="food-options" id="foodOptions">
            <!-- Tùy chọn món: Size, Topping, Lượng đường, Lượng đá -->
        </section>
    </div>
</main>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
