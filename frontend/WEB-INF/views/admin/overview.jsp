<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="adminPage" value="overview"/>
<c:set var="pageTitle" value="Tổng quan"/>
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>
<main class="admin-content">
    <header class="page-header"><div><h1>Tổng quan</h1><p>Tình hình kinh doanh và các đơn hàng cần chú ý trong 7 ngày gần đây.</p></div><a class="button outline" href="<c:url value='/admin/reports'/>">Xem báo cáo</a></header>
    <div id="dashboardAlert" class="alert" hidden role="alert"></div>
    <section class="stats-grid">
        <div class="stat-card"><span class="stat-icon">₫</span><div><span class="stat-label">Doanh thu đã thanh toán</span><strong class="stat-num" id="summaryRevenue">—</strong></div></div>
        <div class="stat-card"><span class="stat-icon"><svg class="icon"><use href="#icon-orders"/></svg></span><div><span class="stat-label">Đơn hàng trong kỳ</span><strong class="stat-num" id="summaryOrders">—</strong></div></div>
        <div class="stat-card"><span class="stat-icon"><svg class="icon"><use href="#icon-users"/></svg></span><div><span class="stat-label">Tổng khách hàng</span><strong class="stat-num" id="summaryCustomers">—</strong></div></div>
        <div class="stat-card"><span class="stat-icon">◷</span><div><span class="stat-label">Đơn hàng đang xử lý</span><strong class="stat-num" id="summaryPending">—</strong></div></div>
    </section>
    <div class="dashboard-grid"><section class="admin-card"><div class="card-header"><h2>Doanh thu theo ngày</h2><span id="chartRange" class="text-muted"></span></div><div class="card-body"><div class="revenue-chart" id="revenueChart" aria-label="Biểu đồ doanh thu theo ngày"></div></div></section><section class="admin-card"><div class="card-header"><h2>Đơn hàng theo trạng thái</h2></div><div class="card-body"><div class="status-breakdown" id="statusBreakdown"></div></div></section></div>
    <div class="dashboard-grid"><section class="admin-card"><div class="card-header"><h2>Đơn hàng gần đây</h2><a href="<c:url value='/admin/orders'/>">Xem tất cả →</a></div><div class="table-responsive"><table class="admin-table"><thead><tr><th>Mã đơn</th><th>Khách hàng</th><th>Tổng tiền</th><th>Trạng thái</th></tr></thead><tbody id="recentOrders"><tr><td colspan="4" class="loading-state">Đang tải dữ liệu...</td></tr></tbody></table></div></section><section class="admin-card"><div class="card-header"><h2>Món ăn bán chạy nhất</h2></div><div class="card-body" id="topItems"></div></section></div>
    <p class="report-note">Doanh thu gồm đơn đã hoàn tất và thanh toán thành công; không gồm đơn hủy hoặc đã hoàn tiền.</p>
</main>
<script src="<c:url value='/assets/js/admin-dashboard.js'/>"></script>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
