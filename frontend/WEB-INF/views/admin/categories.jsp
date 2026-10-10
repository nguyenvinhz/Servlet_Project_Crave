<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/views/components/header.jspf" %>

<main class="page-container admin-page">
    <header class="admin-header">
        <div class="admin-header-title">
            <p class="eyebrow">Trang quản trị</p>
            <h1>Quản lý danh mục món ăn</h1>
            <p class="lead">Tạo mới, chỉnh sửa thông tin hoặc xóa danh mục (chỉ xóa được danh mục chưa có món ăn).</p>
        </div>
        <div class="admin-header-actions">
            <button type="button" class="button primary" onclick="openCreateCategoryModal()">
                <span>＋ Thêm danh mục mới</span>
            </button>
            <a href="<c:url value='/admin/menu'/>" class="button outline">
                <span>◀ Quay lại quản lý món</span>
            </a>
        </div>
    </header>

    <!-- Bảng danh mục -->
    <section class="admin-card">
        <div class="table-responsive">
            <table class="admin-table" id="adminCategoryTable">
                <thead>
                    <tr>
                        <th style="width: 100px;">Mã DM</th>
                        <th>Tên danh mục</th>
                        <th>Mô tả</th>
                        <th style="width: 140px; text-align: center;">Số lượng món</th>
                        <th style="width: 150px; text-align: right;">Hành động</th>
                    </tr>
                </thead>
                <tbody>
                    <c:if test="${empty categories}">
                        <tr>
                            <td colspan="5" class="text-center py-4">
                                <div class="empty-state-table">
                                    <span>📁</span>
                                    <p>Chưa có danh mục nào được khởi tạo.</p>
                                </div>
                            </td>
                        </tr>
                    </c:if>
                    <c:forEach var="cat" items="${categories}">
                        <tr id="catRow-${cat.id}">
                            <td><code>${cat.id}</code></td>
                            <td>
                                <strong>${cat.name}</strong>
                            </td>
                            <td>
                                <span class="text-muted">${not empty cat.description ? cat.description : '—'}</span>
                            </td>
                            <td style="text-align: center;">
                                <a href="<c:url value='/admin/menu?categoryId=${cat.id}'/>" class="badge count-badge" title="Xem các món trong danh mục này">
                                    ${cat.foodCount} món
                                </a>
                            </td>
                            <td style="text-align: right;">
                                <div class="row-actions">
                                    <button type="button" class="action-btn edit-btn"
                                            onclick="openEditCategoryModal('${cat.id}', '${cat.name}', '${cat.description}')"
                                            title="Sửa danh mục">
                                        ✏️ Sửa
                                    </button>
                                    <button type="button" class="action-btn delete-btn"
                                            onclick="deleteCategory('${cat.id}', '${cat.name}', ${cat.foodCount})"
                                            title="${cat.foodCount > 0 ? 'Không thể xóa danh mục đang có món' : 'Xóa danh mục'}"
                                            ${cat.foodCount > 0 ? 'disabled style="opacity: 0.5; cursor: not-allowed;"' : ''}>
                                        🗑️ Xóa
                                    </button>
                                </div>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </div>
    </section>
</main>

<!-- Modal Thêm / Chỉnh sửa danh mục -->
<div class="modal-backdrop" id="categoryModal" style="display: none;">
    <div class="modal-dialog">
        <div class="modal-header">
            <h3 id="categoryModalTitle">Thêm danh mục mới</h3>
            <button type="button" class="modal-close" onclick="closeCategoryModal()">&times;</button>
        </div>
        <form id="categoryForm" onsubmit="event.preventDefault(); submitCategoryForm();">
            <input type="hidden" id="categoryModalId" value="">
            <div class="modal-body">
                <div id="categoryFormAlert" class="alert-box" style="display: none;"></div>

                <div class="form-group">
                    <label for="catNameInput">Tên danh mục *</label>
                    <input type="text" id="catNameInput" placeholder="Ví dụ: Đồ uống, Món chính..." required maxlength="100">
                </div>

                <div class="form-group">
                    <label for="catDescInput">Mô tả danh mục</label>
                    <textarea id="catDescInput" rows="3" placeholder="Mô tả các món thuộc danh mục này..." maxlength="255"></textarea>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="button outline" onclick="closeCategoryModal()">Hủy bỏ</button>
                <button type="submit" class="button primary" id="catSubmitBtn">Lưu danh mục</button>
            </div>
        </form>
    </div>
</div>

<!-- Toast Feedback Container -->
<div class="toast-container" id="toastContainer"></div>

<script src="<c:url value='/assets/js/menu-admin.js'/>"></script>

<%@ include file="/WEB-INF/views/components/footer.jspf" %>
