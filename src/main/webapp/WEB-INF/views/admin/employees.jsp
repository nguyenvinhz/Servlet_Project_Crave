<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="adminPage" value="employees" />
<c:set var="pageTitle" value="Quản lý nhân viên" />
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>
<link rel="stylesheet" href="<c:url value='/assets/css/admin-management.css?v=20261013-palette2' />">
<main class="admin-content">
    <header class="management-heading"><div><p class="management-eyebrow">Nhân sự</p><h1>Quản lý nhân viên</h1>
        <p>Quản lý chức vụ, thông tin liên hệ và trạng thái làm việc của đội ngũ.</p></div>
        <div class="management-actions"><button type="button" class="button outline" id="managementRefresh">Tải lại</button>
            <button type="button" class="button primary" id="employeeCreate">＋ Thêm nhân viên</button></div></header>
    <div id="managementFeedback" class="management-feedback" role="status" aria-live="polite" data-tone="${not empty sessionScope.adminError ? 'error' : 'success'}" ${empty sessionScope.adminError and empty sessionScope.adminSuccess ? 'hidden' : ''}><c:out value="${not empty sessionScope.adminError ? sessionScope.adminError : sessionScope.adminSuccess}"/></div><c:remove var="adminError" scope="session"/><c:remove var="adminSuccess" scope="session"/>
    <section class="admin-card management-summary" aria-label="Thống kê nhân viên">
        <div><span>Tổng nhân viên</span><strong id="managementTotal">—</strong></div>
        <div><span>Đang làm việc</span><strong id="managementActive">—</strong></div>
        <div><span>Đã nghỉ việc</span><strong id="managementInactive">—</strong></div>
    </section>
    <div class="admin-toolbar management-toolbar">
        <label class="management-search"><span class="sr-only">Tìm nhân viên</span><input id="managementSearch" type="search"
            placeholder="Tìm theo tên, mã nhân viên hoặc email…" autocomplete="off"></label>
        <label><span class="sr-only">Lọc chức vụ</span><select id="managementRole"><option value="">Mọi chức vụ</option>
            <option value="ADMIN">Quản trị viên</option><option value="MENU_MANAGER">Quản lý thực đơn</option>
            <option value="ORDER_STAFF">Xử lý đơn hàng</option><option value="PROMOTION_MANAGER">Quản lý khuyến mãi</option>
            <option value="HR_MANAGER">Quản lý nhân sự</option></select></label>
        <label><span class="sr-only">Lọc trạng thái</span><select id="managementStatus"><option value="">Mọi trạng thái</option>
            <option value="WORKING">Đang làm việc</option><option value="ON_LEAVE">Đã nghỉ việc</option></select></label>
        <span id="managementCount" class="management-count" aria-live="polite"></span>
    </div>
    <section class="admin-card management-table-wrap"><table class="admin-table"><thead><tr>
        <th>Nhân viên</th><th>Chức vụ</th><th>Số điện thoại</th><th>Email</th><th>Ngày vào làm</th><th>Trạng thái</th><th>Thao tác</th>
        </tr></thead><tbody id="managementRows"><c:forEach var="item" items="${accounts}"><tr data-account data-id="<c:out value='${item.id()}'/>" data-full-name="<c:out value='${item.fullName()}'/>" data-email="<c:out value='${item.email()}'/>" data-phone="<c:out value='${item.phone()}'/>" data-status="${item.status().name()}" data-role="${item.role().name()}">
<td><div class="management-identity"><span class="management-avatar"></span><div><strong><c:out value="${item.fullName()}"/></strong><small><c:out value="${item.id()}"/></small></div></div></td>
<td data-role-label></td><td><c:out value="${item.phone()}"/></td><td><c:out value="${item.email()}"/></td><td data-date="<c:out value='${item.hireDate()}'/>"></td>

<td><span class="management-badge" data-tone="${item.status().name() == 'WORKING' ? 'active' : 'inactive'}">${item.status().name() == 'WORKING' ? 'Đang làm việc' : 'Đã nghỉ việc'}</span></td>
<td><div class="management-row-actions">
<c:choose><c:when test="${item.id() == sessionScope.employeeId or (item.role().name() == 'ADMIN' and sessionScope.role != 'ADMIN')}"><span class="management-current-badge">${item.id() == sessionScope.employeeId ? 'Tài khoản hiện tại' : 'Quản trị viên quản lý'}</span></c:when><c:otherwise>
<div class="management-action-menu"><button type="button" class="management-dots-btn" aria-label="Thao tác với <c:out value='${item.fullName()}'/>" aria-haspopup="true" aria-expanded="false" title="Thao tác"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="12" cy="5" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="12" cy="19" r="1.8"/></svg></button><div class="management-action-dropdown" role="menu">
<button type="button" class="management-dropdown-item" role="menuitem" data-role-change>Đổi chức vụ</button>
<button type="button" class="management-dropdown-item ${item.status().name() == 'WORKING' ? 'management-dropdown-item-danger' : ''}" role="menuitem" data-status-change>${item.status().name() == 'WORKING' ? 'Cho nghỉ việc' : 'Làm việc lại'}</button></div></div></c:otherwise></c:choose>
</div></td></tr></c:forEach><c:if test="${empty accounts}"><tr class="management-no-results"><td colspan="7" class="management-empty">Không tìm thấy nhân viên phù hợp.</td></tr></c:if></tbody></table></section>
    <dialog id="employeeCreateDialog" class="modal-dialog admin-management-dialog" aria-labelledby="employeeCreateTitle">
        <form id="employeeCreateForm" method="post" action="<c:url value='/admin/employees/action'/>"><input type="hidden" name="action" value="create"><div class="modal-header"><h2 id="employeeCreateTitle">Thêm nhân viên</h2>
            <button type="button" class="management-close" data-close-dialog aria-label="Đóng">×</button></div>
            <div class="modal-body management-form"><div class="form-group"><label for="employeeFullName">Họ và tên</label>
                <input id="employeeFullName" name="fullName" required maxlength="100" autocomplete="name"></div>
                <div class="form-row"><div class="form-group"><label for="employeeEmail">Email</label>
                    <input id="employeeEmail" name="email" type="email" required maxlength="100" autocomplete="email"></div>
                    <div class="form-group"><label for="employeePhone">Số điện thoại</label>
                        <input id="employeePhone" name="phone" type="tel" required minlength="9" maxlength="15" autocomplete="tel"></div></div>
                <div class="form-group"><label for="employeePassword">Mật khẩu ban đầu</label>
                    <input id="employeePassword" name="password" type="password" required minlength="8" maxlength="72" autocomplete="new-password">
                    <small>Gồm 8–72 ký tự. Cung cấp mật khẩu cho nhân viên để đăng nhập.</small></div>
                <div class="form-row"><div class="form-group"><label for="employeeRole">Chức vụ</label><select id="employeeRole" name="role" required>
                    <option value="ORDER_STAFF">Xử lý đơn hàng</option><option value="MENU_MANAGER">Quản lý thực đơn</option>
                    <option value="PROMOTION_MANAGER">Quản lý khuyến mãi</option><option value="HR_MANAGER">Quản lý nhân sự</option>
                    <option value="ADMIN" data-admin-only>Quản trị viên</option></select></div>
                    <div class="form-group"><label for="employeeHireDate">Ngày vào làm</label><input id="employeeHireDate" name="hireDate" type="date" required></div></div>
                <div class="form-group"><label for="employeeAddress">Địa chỉ</label><input id="employeeAddress" name="address" maxlength="255" autocomplete="street-address"></div>
                <p id="employeeCreateError" class="management-form-error" role="alert" hidden></p></div>
            <div class="modal-footer"><button type="button" class="button outline" data-close-dialog>Hủy</button>
                <button type="submit" class="button primary">Tạo nhân viên</button></div></form>
    </dialog>
    <dialog id="employeeRoleDialog" class="modal-dialog admin-management-dialog" aria-labelledby="employeeRoleTitle">
        <form id="employeeRoleForm" method="post" action="<c:url value='/admin/employees/action'/>"><input type="hidden" name="action" value="role"><input type="hidden" name="id"><div class="modal-header"><h2 id="employeeRoleTitle">Đổi chức vụ</h2>
            <button type="button" class="management-close" data-close-dialog aria-label="Đóng">×</button></div>
            <div class="modal-body management-form"><p id="employeeRoleName"></p><div class="form-group"><label for="employeeNewRole">Chức vụ mới</label>
                <select id="employeeNewRole" name="role" required><option value="ORDER_STAFF">Xử lý đơn hàng</option>
                    <option value="MENU_MANAGER">Quản lý thực đơn</option><option value="PROMOTION_MANAGER">Quản lý khuyến mãi</option>
                    <option value="HR_MANAGER">Quản lý nhân sự</option><option value="ADMIN" data-admin-only>Quản trị viên</option></select></div>
                <p id="employeeRoleError" class="management-form-error" role="alert" hidden></p></div>
            <div class="modal-footer"><button type="button" class="button outline" data-close-dialog>Hủy</button><button type="submit" class="button primary">Lưu chức vụ</button></div></form>
    </dialog>
    <dialog id="managementConfirm" class="modal-dialog admin-management-dialog" aria-labelledby="managementConfirmTitle">
        <form id="managementConfirmForm" method="post" action="<c:url value='/admin/employees/action'/>"><input type="hidden" name="action" value="status"><input type="hidden" name="id"><input type="hidden" name="status"><div class="modal-header"><h2 id="managementConfirmTitle">Cập nhật nhân viên</h2>
            <button type="button" class="management-close" data-close-dialog aria-label="Đóng">×</button></div>
            <div class="modal-body"><p id="managementConfirmMessage"></p></div>
            <div class="modal-footer"><button type="button" class="button outline" data-close-dialog>Hủy</button>
                <button type="submit" class="button primary" id="managementConfirmSubmit">Xác nhận</button></div></form>
    </dialog>
</main>
<script src="<c:url value='/assets/js/admin-management.js?v=20261017-forms1' />" defer></script>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
