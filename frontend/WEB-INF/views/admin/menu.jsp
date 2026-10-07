<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container">
    <header class="page-heading">
        <p class="eyebrow">Trang quản trị</p>
        <h1>Quản lý thực đơn & Món ăn</h1>
        <p class="lead">Thêm mới món ăn, chỉnh sửa thông tin, giá bán và cấu hình tùy chọn món (size, topping).</p>
    </header>

    <div class="admin-actions">
        <a href="<c:url value='/admin/categories'/>" class="btn">Quản lý danh mục</a>
    </div>

    <section class="admin-table-container">
        <!-- Bảng danh sách món ăn quản trị -->
    </section>
</main>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
