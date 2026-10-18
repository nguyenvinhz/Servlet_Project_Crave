<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="adminPage" value="customers" />
<c:set var="pageTitle" value="Quản lý khách hàng" />
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>
<link rel="stylesheet" href="<c:url value='/assets/css/admin-management.css?v=20261013-palette2' />">
<main class="admin-content">
    <header class="management-heading">
        <div><p class="management-eyebrow">Tài khoản</p><h1>Quản lý khách hàng</h1>
            <p>Xem thông tin khách hàng, số đơn đã đặt và quản lý trạng thái tài khoản.</p></div>
        <button type="button" class="button outline" id="managementRefresh">Tải lại</button>
    </header>
    <div id="managementFeedback" class="management-feedback" role="status" aria-live="polite" data-tone="${not empty sessionScope.adminError ? 'error' : 'success'}" ${empty sessionScope.adminError and empty sessionScope.adminSuccess ? 'hidden' : ''}><c:out value="${not empty sessionScope.adminError ? sessionScope.adminError : sessionScope.adminSuccess}"/></div><c:remove var="adminError" scope="session"/><c:remove var="adminSuccess" scope="session"/>
    <section class="admin-card management-summary" aria-label="Thống kê khách hàng">
        <div><span>Tổng khách hàng</span><strong id="managementTotal">—</strong></div>
        <div><span>Đang hoạt động</span><strong id="managementActive">—</strong></div>
        <div><span>Đã khóa</span><strong id="managementInactive">—</strong></div>
    </section>
    <div class="admin-toolbar management-toolbar">
        <label class="management-search"><span class="sr-only">Tìm khách hàng</span>
            <input id="managementSearch" type="search" placeholder="Tìm theo tên, số điện thoại hoặc email…" autocomplete="off"></label>
        <label><span class="sr-only">Lọc trạng thái</span><select id="managementStatus">
            <option value="">Mọi trạng thái</option><option value="ACTIVE">Hoạt động</option><option value="LOCKED">Đã khóa</option>
        </select></label>
        <span id="managementCount" class="management-count" aria-live="polite"></span>
    </div>
    <section class="admin-card management-table-wrap">
        <table class="admin-table"><thead><tr><th>Khách hàng</th><th>Số điện thoại</th><th>Email</th><th>Ngày đăng ký</th>
            <th>Số đơn hàng</th><th>Đã chi tiêu</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
            <tbody id="managementRows"><c:forEach var="item" items="${accounts}"><tr data-account data-id="<c:out value='${item.id()}'/>" data-full-name="<c:out value='${item.fullName()}'/>" data-email="<c:out value='${item.email()}'/>" data-phone="<c:out value='${item.phone()}'/>" data-status="${item.status().name()}">
<td><div class="management-identity"><span class="management-avatar"></span><div><strong><c:out value="${item.fullName()}"/></strong><small><c:out value="${item.id()}"/></small></div></div></td>
<td><c:out value="${item.phone()}"/></td><td><c:out value="${item.email()}"/></td><td data-date="<c:out value='${item.registeredAt()}'/>"></td>
<td><c:out value="${item.orderCount()}"/></td><td><fmt:formatNumber value="${item.totalSpent()}" groupingUsed="true"/> ₫</td>
<td><span class="management-badge" data-tone="${item.status().name() == 'ACTIVE' ? 'active' : 'locked'}">${item.status().name() == 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'}</span></td>
<td><div class="management-row-actions">

<div class="management-action-menu"><button type="button" class="management-dots-btn" aria-label="Thao tác với <c:out value='${item.fullName()}'/>" aria-haspopup="true" aria-expanded="false" title="Thao tác"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="12" cy="5" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="12" cy="19" r="1.8"/></svg></button><div class="management-action-dropdown" role="menu">

<button type="button" class="management-dropdown-item ${item.status().name() == 'ACTIVE' ? 'management-dropdown-item-danger' : ''}" role="menuitem" data-status-change>${item.status().name() == 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</button></div></div>
</div></td></tr></c:forEach><c:if test="${empty accounts}"><tr class="management-no-results"><td colspan="8" class="management-empty">Không tìm thấy khách hàng phù hợp.</td></tr></c:if></tbody>
        </table>
    </section>
    <dialog id="managementConfirm" class="modal-dialog admin-management-dialog" aria-labelledby="managementConfirmTitle">
        <form id="managementConfirmForm" method="post" action="<c:url value='/admin/customers/action'/>"><input type="hidden" name="action" value="status"><input type="hidden" name="id"><input type="hidden" name="status"><div class="modal-header"><h2 id="managementConfirmTitle">Cập nhật tài khoản</h2>
            <button type="button" class="management-close" data-close-dialog aria-label="Đóng">×</button></div>
            <div class="modal-body"><p id="managementConfirmMessage"></p></div>
            <div class="modal-footer"><button type="button" class="button outline" data-close-dialog>Hủy</button>
                <button type="submit" class="button primary" id="managementConfirmSubmit">Xác nhận</button></div></form>
    </dialog>
</main>
<script src="<c:url value='/assets/js/admin-management.js?v=20261017-forms1' />" defer></script>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
