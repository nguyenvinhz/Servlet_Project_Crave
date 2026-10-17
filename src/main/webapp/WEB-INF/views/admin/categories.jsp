<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="adminPage" value="categories"/>
<c:set var="pageTitle" value="Danh mục"/>
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>

<c:if test="${not empty sessionScope.adminError or not empty sessionScope.adminSuccess}"><div id="adminFlash" hidden data-message="<c:out value='${not empty sessionScope.adminError ? sessionScope.adminError : sessionScope.adminSuccess}'/>" data-type="${not empty sessionScope.adminError ? 'error' : 'success'}"></div><c:remove var="adminError" scope="session"/><c:remove var="adminSuccess" scope="session"/></c:if>
<main class="admin-content page-container admin-page">
    <header class="admin-header">
        <div class="admin-header-title">
            <h1>Danh mục</h1>
            <p class="lead">Sắp xếp thực đơn theo nhóm món ăn và thức uống.</p>
        </div>
        <div class="admin-header-actions">
            <button type="button" class="button primary" onclick="openCreateCategoryModal()">
                <span>＋ Thêm danh mục mới</span>
            </button>
            <a href="<c:url value='/admin/menu'/>" class="button outline">
                <span>Quản lý món ăn</span>
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
                        <tr id="catRow-<c:out value='${cat.id}'/>">
                            <td><code><c:out value="${cat.id}"/></code></td>
                            <td>
                                <strong><c:out value="${cat.name}"/></strong>
                            </td>
                            <td>
                                <span class="text-muted"><c:out value="${not empty cat.description ? cat.description : '—'}"/></span>
                            </td>
                            <td style="text-align: center;">
                                <a href="<c:url value='/admin/menu'><c:param name='categoryId' value='${cat.id}'/></c:url>" class="badge count-badge" title="Xem các món trong danh mục này">
                                    <c:out value="${cat.foodCount}"/> món
                                </a>
                            </td>
                            <td style="text-align: right;">
                                <div class="row-actions">
                                    <button type="button" class="action-btn edit-btn"
                                            data-cat-id="<c:out value='${cat.id}'/>"
                                            data-cat-name="<c:out value='${cat.name}'/>"
                                            data-cat-desc="<c:out value='${cat.description}'/>"
                                            onclick="handleEditCategoryClick(this)"
                                            title="Sửa danh mục">
                                        Sửa
                                    </button>
                                    <button type="button" class="action-btn delete-btn"
                                            data-cat-id="<c:out value='${cat.id}'/>"
                                            data-cat-name="<c:out value='${cat.name}'/>"
                                            data-food-count="<c:out value='${cat.foodCount}'/>"
                                            onclick="handleDeleteCategoryClick(this)"
                                            title="${cat.foodCount > 0 ? 'Không thể xóa danh mục đang có món' : 'Xóa danh mục'}"
                                            ${cat.foodCount > 0 ? 'disabled' : ''}>
                                        Xóa
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
            <button type="button" class="modal-close" onclick="closeCategoryModal()" aria-label="Đóng hộp thoại">&times;</button>
        </div>
        <form id="categoryForm" method="post" action="<c:url value='/admin/categories/action'/>"><input type="hidden" name="action" value="create">
            <input type="hidden" id="categoryModalId" name="id" value="">
            <div class="modal-body">
                <div id="categoryFormAlert" class="alert-box" style="display: none;"></div>

                <div class="form-group">
                    <label for="catNameInput">Tên danh mục *</label>
                    <input type="text" id="catNameInput" name="name" placeholder="Ví dụ: Đồ uống, Món chính..." required maxlength="100">
                </div>

                <div class="form-group">
                    <label for="catDescInput">Mô tả danh mục</label>
                    <textarea id="catDescInput" name="description" rows="3" placeholder="Mô tả các món thuộc danh mục này..." maxlength="255"></textarea>
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

<script src="<c:url value='/assets/js/menu-admin.js?v=20261017-forms1'/>"></script>

<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
