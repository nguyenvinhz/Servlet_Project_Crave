<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="adminPage" value="menu"/>
<c:set var="pageTitle" value="Món ăn - Thức uống"/>
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>

<main class="page-container admin-page">
    <header class="admin-header">
        <div class="admin-header-title">
            <h1>Món ăn - Thức uống</h1>
            <p class="lead">Quản lý thực đơn, giá bán và các tùy chọn cho từng món.</p>
        </div>
        <div class="admin-header-actions">
            <button type="button" class="button primary" onclick="openCreateFoodModal()">
                <span>＋ Thêm món mới</span>
            </button>
            <a href="<c:url value='/admin/categories'/>" class="button outline">
                <span>Quản lý danh mục</span>
            </a>
        </div>
    </header>

    <!-- Thống kê nhanh -->
    <section class="admin-stats-grid">
        <div class="stat-card">
            <span class="stat-icon">🍲</span>
            <div class="stat-info">
                <span class="stat-num">${foods.size()}</span>
                <span class="stat-label">Tổng món ăn</span>
            </div>
        </div>
        <div class="stat-card">
            <span class="stat-icon">🟢</span>
            <div class="stat-info">
                <c:set var="availCount" value="0" />
                <c:forEach var="f" items="${foods}">
                    <c:if test="${f.status == 'AVAILABLE'}"><c:set var="availCount" value="${availCount + 1}" /></c:if>
                </c:forEach>
                <span class="stat-num">${availCount}</span>
                <span class="stat-label">Đang phục vụ</span>
            </div>
        </div>
        <div class="stat-card">
            <span class="stat-icon">🔴</span>
            <div class="stat-info">
                <c:set var="unavailCount" value="0" />
                <c:forEach var="f" items="${foods}">
                    <c:if test="${f.status == 'UNAVAILABLE'}"><c:set var="unavailCount" value="${unavailCount + 1}" /></c:if>
                </c:forEach>
                <span class="stat-num">${unavailCount}</span>
                <span class="stat-label">Tạm hết món</span>
            </div>
        </div>
        <div class="stat-card">
            <span class="stat-icon">📑</span>
            <div class="stat-info">
                <span class="stat-num">${categories.size()}</span>
                <span class="stat-label">Danh mục món</span>
            </div>
        </div>
    </section>

    <!-- Bộ lọc & Tìm kiếm -->
    <section class="admin-toolbar">
        <form action="<c:url value='/admin/menu'/>" method="GET" class="admin-filter-form">
            <div class="filter-group search-filter">
                <input type="text" name="keyword" value="<c:out value='${keyword}'/>" placeholder="Tìm theo tên món ăn..." aria-label="Tìm kiếm món">
            </div>
            <div class="filter-group">
                <select name="categoryId" aria-label="Lọc theo danh mục">
                    <option value="">Tất cả danh mục</option>
                    <c:forEach var="cat" items="${categories}">
                        <option value="<c:out value='${cat.id}'/>" ${currentCategoryId == cat.id ? 'selected' : ''}><c:out value="${cat.name}"/> (<c:out value="${cat.foodCount}"/>)</option>
                    </c:forEach>
                </select>
            </div>
            <div class="filter-group">
                <select name="status" aria-label="Lọc theo trạng thái">
                    <option value="">Tất cả trạng thái</option>
                    <option value="AVAILABLE" ${currentStatus == 'AVAILABLE' ? 'selected' : ''}>Đang bán</option>
                    <option value="UNAVAILABLE" ${currentStatus == 'UNAVAILABLE' ? 'selected' : ''}>Tạm hết món</option>
                </select>
            </div>
            <c:if test="${not empty keyword or not empty currentCategoryId or not empty currentStatus}">
                <a href="<c:url value='/admin/menu'/>" class="button link-btn">Đặt lại</a>
            </c:if>
        </form>
    </section>

    <!-- Bảng danh sách món ăn -->
    <section class="admin-card">
        <div class="table-responsive">
            <table class="admin-table" id="adminFoodTable">
                <thead>
                    <tr>
                        <th style="width: 70px;">Ảnh</th>
                        <th style="width: 80px;">Mã</th>
                        <th>Tên món ăn</th>
                        <th>Danh mục</th>
                        <th>Giá bán</th>
                        <th>Trạng thái</th>
                        <th style="width: 100px; text-align: right;">Hành động</th>
                    </tr>
                </thead>
                <tbody>
                    <c:if test="${empty foods}">
                        <tr>
                            <td colspan="7" class="text-center py-4">
                                <div class="empty-state-table">
                                    <span>🔍</span>
                                    <p>Không tìm thấy món ăn nào phù hợp với bộ lọc.</p>
                                </div>
                            </td>
                        </tr>
                    </c:if>
                    <c:forEach var="food" items="${foods}">
                        <tr id="foodRow-<c:out value='${food.id}'/>">
                            <td>
                                <img src="<c:out value='${not empty food.imageUrl ? food.imageUrl : pageContext.request.contextPath.concat(\"/assets/images/food-placeholder.svg\")}'/>"
                                     alt="<c:out value='${food.name}'/>"
                                     class="admin-thumb"
                                     onerror="this.onerror=null;this.src='${pageContext.request.contextPath}/assets/images/food-placeholder.svg';">
                            </td>
                            <td><code><c:out value="${food.id}"/></code></td>
                            <td>
                                <div class="food-name-cell">
                                    <strong><c:out value="${food.name}"/></strong>
                                    <small class="text-muted line-clamp"><c:out value="${food.description}"/></small>
                                </div>
                            </td>
                            <td><span class="badge category-badge"><c:out value="${food.categoryName}"/></span></td>
                            <td><strong class="price-text"><fmt:formatNumber value="${food.price}" type="number" groupingUsed="true"/> ₫</strong></td>
                            <td>
                                <span class="badge ${food.status == 'AVAILABLE' ? 'green' : 'red'}">
                                    ${food.status == 'AVAILABLE' ? '● Đang bán' : '○ Tạm hết'}
                                </span>
                            </td>
                            <td style="text-align: right;">
                                <div class="row-actions">
                                    <button type="button" class="action-btn"
                                            data-food-id="<c:out value='${food.id}'/>"
                                            data-food-name="<c:out value='${food.name}'/>"
                                            onclick="handleOpenOptionsClick(this)">Quản lý tùy chọn</button>
                                    <button type="button" class="action-btn"
                                            data-food-id="<c:out value='${food.id}'/>"
                                            data-status="<c:out value='${food.status}'/>"
                                            onclick="handleToggleStatusClick(this)">${food.status == 'AVAILABLE' ? 'Tạm ngừng bán' : 'Mở bán món'}</button>
                                    <button type="button" class="action-btn edit-btn"
                                            data-food-id="<c:out value='${food.id}'/>"
                                            onclick="handleEditFoodClick(this)"
                                            title="Sửa món">
                                        Sửa
                                    </button>
                                    <button type="button" class="action-btn delete-btn"
                                            data-food-id="<c:out value='${food.id}'/>"
                                            data-food-name="<c:out value='${food.name}'/>"
                                            onclick="handleDeleteFoodClick(this)"
                                            title="Xóa món">
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

<!-- Modal Thêm / Chỉnh sửa món ăn -->
<div class="modal-backdrop" id="foodModal" style="display: none;">
    <div class="modal-dialog">
        <div class="modal-header">
            <h3 id="foodModalTitle">Thêm món ăn mới</h3>
            <button type="button" class="modal-close" onclick="closeFoodModal()" aria-label="Đóng hộp thoại">&times;</button>
        </div>
        <form id="foodForm" onsubmit="event.preventDefault(); submitFoodForm();">
            <input type="hidden" id="foodModalId" value="">
            <div class="modal-body">
                <div id="foodFormAlert" class="alert-box" style="display: none;"></div>

                <div class="form-group">
                    <label for="foodCategorySelect">Danh mục món ăn *</label>
                    <select id="foodCategorySelect" required>
                        <option value="">-- Chọn danh mục --</option>
                        <c:forEach var="c" items="${categories}">
                            <option value="<c:out value='${c.id}'/>"><c:out value="${c.name}"/></option>
                        </c:forEach>
                    </select>
                </div>

                <div class="form-group">
                    <label for="foodNameInput">Tên món ăn *</label>
                    <input type="text" id="foodNameInput" placeholder="Ví dụ: Cơm gà xối mỡ" required maxlength="150">
                </div>

                <div class="form-row">
                    <div class="form-group col">
                        <label for="foodPriceInput">Giá bán (VNĐ) *</label>
                        <input type="number" id="foodPriceInput" placeholder="Ví dụ: 45000" min="1000" step="1000" required>
                    </div>
                    <div class="form-group col">
                        <label for="foodStatusSelect">Trạng thái *</label>
                        <select id="foodStatusSelect" required>
                            <option value="AVAILABLE">Đang bán</option>
                            <option value="UNAVAILABLE">Tạm hết</option>
                        </select>
                    </div>
                </div>

                <div class="form-group">
                    <label for="foodImageInput">Đường dẫn hình ảnh (URL)</label>
                    <input type="url" id="foodImageInput" placeholder="https://..." maxlength="512">
                </div>

                <div class="form-group">
                    <label for="foodDescInput">Mô tả món ăn</label>
                    <textarea id="foodDescInput" rows="3" placeholder="Mô tả hương vị, nguyên liệu món..." maxlength="500"></textarea>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="button outline" onclick="closeFoodModal()">Hủy bỏ</button>
                <button type="submit" class="button primary" id="foodSubmitBtn">Lưu món ăn</button>
            </div>
        </form>
    </div>
</div>

<!-- Modal Quản lý tùy chọn của món -->
<div class="modal-backdrop" id="optionsModal" style="display: none;">
    <div class="modal-dialog modal-lg">
        <div class="modal-header">
            <div>
                <h3>Quản lý tùy chọn món</h3>
                <p class="text-muted" id="optionsModalFoodName">Món: ...</p>
            </div>
            <button type="button" class="modal-close" onclick="closeOptionsModal()" aria-label="Đóng hộp thoại">&times;</button>
        </div>
        <div class="modal-body">
            <div id="optionsModalAlert" class="alert-box" style="display: none;"></div>

            <!-- Danh sách tùy chọn hiện có -->
            <div class="options-list-wrap">
                <h4>Các tùy chọn hiện tại</h4>
                <table class="admin-table-sub">
                    <thead>
                        <tr>
                            <th>Nhóm</th>
                            <th>Tên tùy chọn</th>
                            <th>Phụ thu (₫)</th>
                            <th>Trạng thái</th>
                            <th style="width: 80px; text-align: right;">Xóa</th>
                        </tr>
                    </thead>
                    <tbody id="optionsTableBody">
                        <tr><td colspan="5" class="text-center py-2">Đang tải dữ liệu...</td></tr>
                    </tbody>
                </table>
            </div>

            <!-- Form thêm tùy chọn mới -->
            <div class="add-option-box">
                <h4>Thêm tùy chọn mới</h4>
                <form id="addOptionForm" onsubmit="event.preventDefault(); submitAddOption();" class="option-form-inline">
                    <input type="hidden" id="currentOptionFoodId" value="">
                    <div class="inline-fields">
                        <div class="field">
                            <label for="newOptType">Loại tùy chọn</label>
                            <select id="newOptType" required>
                                <option value="SIZE">Kích cỡ</option>
                                <option value="TOPPING">Topping thêm</option>
                                <option value="SUGAR_LEVEL">Mức đường</option>
                                <option value="ICE_LEVEL">Mức đá</option>
                                <option value="OTHER">Khác</option>
                            </select>
                        </div>
                        <div class="field">
                            <label for="newOptName">Tên tùy chọn *</label>
                            <input type="text" id="newOptName" placeholder="VD: Size L, Trứng ốp la" required maxlength="100">
                        </div>
                        <div class="field">
                            <label for="newOptExtra">Phụ thu (₫) *</label>
                            <input type="number" id="newOptExtra" value="0" min="0" step="1000" required>
                        </div>
                        <div class="field btn-field">
                            <button type="submit" class="button primary">＋ Thêm</button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
        <div class="modal-footer">
            <button type="button" class="button outline" onclick="closeOptionsModal()">Đóng</button>
        </div>
    </div>
</div>

<!-- Toast Feedback Container -->
<div class="toast-container" id="toastContainer"></div>

<script src="<c:url value='/assets/js/menu-admin.js'/>"></script>

<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
