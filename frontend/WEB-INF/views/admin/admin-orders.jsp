<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="adminPage" value="orders"/>
<c:set var="pageTitle" value="Đơn hàng"/>
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>
<main class="admin-content">
    <header class="page-header"><div><h1>Đơn hàng</h1><p>Theo dõi đơn hàng, cập nhật tiến độ và xác nhận thanh toán.</p></div><button type="button" id="refreshBtn" class="button outline">Tải lại dữ liệu</button></header>
    <section class="stats-grid" aria-label="Thống kê đơn hàng">
        <div class="stat-card"><span class="stat-icon"><svg class="icon"><use href="#icon-orders"/></svg></span><div><span class="stat-label">Tổng đơn hàng</span><strong class="stat-num" id="orderTotal">—</strong></div></div>
        <div class="stat-card"><span class="stat-icon">◷</span><div><span class="stat-label">Chờ xác nhận</span><strong class="stat-num" id="orderPending">—</strong></div></div>
        <div class="stat-card"><span class="stat-icon">✓</span><div><span class="stat-label">Hoàn tất</span><strong class="stat-num" id="orderCompleted">—</strong></div></div>
        <div class="stat-card"><span class="stat-icon">↗</span><div><span class="stat-label">Đang xử lý</span><strong class="stat-num" id="orderProcessing">—</strong></div></div>
    </section>
    <div id="adminAlert" class="alert" hidden role="alert"></div>
    <div class="admin-toolbar"><div class="filter-row"><div class="search-field"><input id="orderSearch" type="search" placeholder="Tìm mã đơn, tên hoặc số điện thoại..." aria-label="Tìm đơn hàng"></div><select id="orderStatusFilter" aria-label="Lọc trạng thái"><option value="">Tất cả trạng thái</option><option value="PENDING_CONFIRMATION">Chờ xác nhận</option><option value="PREPARING">Đang chuẩn bị</option><option value="DELIVERING">Đang giao</option><option value="COMPLETED">Hoàn tất</option><option value="CANCELLED">Đã hủy</option></select><select id="orderTypeFilter" aria-label="Lọc hình thức nhận"><option value="">Tất cả hình thức</option><option value="DELIVERY">Giao tận nơi</option><option value="PICKUP">Nhận tại cửa hàng</option></select></div></div>
    <section class="admin-card"><div class="table-responsive"><table class="admin-table"><thead><tr><th>Mã đơn</th><th>Khách hàng</th><th>Thời gian</th><th>Tổng tiền</th><th>Trạng thái</th><th>Thanh toán</th><th>Thao tác</th></tr></thead><tbody id="adminOrderList"><tr><td colspan="7" class="loading-state">Đang tải đơn hàng...</td></tr></tbody></table></div><div class="table-footer"><span id="orderResultCount"></span><div><button class="button outline" id="orderPrev" type="button">Trước</button> <button class="button outline" id="orderNext" type="button">Sau</button></div></div></section>
</main>
<div id="orderDetailModal" class="modal-backdrop" hidden role="dialog" aria-modal="true" aria-labelledby="orderDetailTitle"><div class="modal-dialog modal-lg"><div class="modal-header"><div><h3 id="orderDetailTitle">Chi tiết đơn hàng</h3><p id="orderDetailSubtitle" class="text-muted"></p></div><button type="button" class="modal-close" id="closeOrderDetail" aria-label="Đóng chi tiết">×</button></div><div id="orderDetailBody" class="modal-body"></div><div class="modal-footer"><button class="button outline" id="closeOrderDetailFooter" type="button">Đóng</button></div></div></div>
<script src="<c:url value='/assets/js/admin-orders.js'/>"></script>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
