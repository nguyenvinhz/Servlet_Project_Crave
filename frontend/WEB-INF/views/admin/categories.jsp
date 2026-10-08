<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container">
    <header class="page-heading">
        <p class="eyebrow">Trang quản trị</p>
        <h1>Quản lý danh mục món ăn</h1>
        <p class="lead">Thêm mới, cập nhật thông tin danh mục hoặc kiểm tra số lượng món thuộc danh mục.</p>
    </header>

    <div class="admin-actions">
        <a href="<c:url value='/admin/menu'/>" class="btn">Quay lại quản lý món</a>
    </div>

    <section class="admin-table-container">
        <!-- Bảng danh mục món ăn -->
    </section>
</main>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
