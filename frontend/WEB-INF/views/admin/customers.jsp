<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
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
    <div id="managementFeedback" class="management-feedback" role="status" aria-live="polite" hidden></div>
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
            <tbody id="managementRows"><tr><td colspan="8" class="management-empty">Đang tải khách hàng…</td></tr></tbody>
        </table>
    </section>
    <dialog id="managementConfirm" class="modal-dialog admin-management-dialog" aria-labelledby="managementConfirmTitle">
        <form id="managementConfirmForm"><div class="modal-header"><h2 id="managementConfirmTitle">Cập nhật tài khoản</h2>
            <button type="button" class="management-close" data-close-dialog aria-label="Đóng">×</button></div>
            <div class="modal-body"><p id="managementConfirmMessage"></p></div>
            <div class="modal-footer"><button type="button" class="button outline" data-close-dialog>Hủy</button>
                <button type="submit" class="button primary" id="managementConfirmSubmit">Xác nhận</button></div></form>
    </dialog>
</main>
<script src="<c:url value='/assets/js/admin-management.js' />" defer></script>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
