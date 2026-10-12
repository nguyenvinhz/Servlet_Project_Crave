<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="adminPage" value="reports"/>
<c:set var="pageTitle" value="Báo cáo - Thống kê"/>
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>
<main class="admin-content">
    <header class="page-header"><div><h1>Báo cáo - Thống kê</h1><p>Phân tích doanh thu, số đơn và hiệu quả bán hàng theo khoảng thời gian.</p></div><button class="button outline" id="exportReport" type="button" disabled>Xuất CSV</button></header>
    <form id="reportForm" class="admin-toolbar report-filters"><label for="reportFrom">Từ ngày</label><input id="reportFrom" type="date" required><label for="reportTo">Đến ngày</label><input id="reportTo" type="date" required><button type="submit" class="button primary">Xem báo cáo</button><select id="reportQuickRange" aria-label="Chọn khoảng thời gian"><option value="7">7 ngày gần đây</option><option value="30">30 ngày gần đây</option><option value="90">90 ngày gần đây</option></select></form>
    <div id="dashboardAlert" class="alert" hidden role="alert"></div>
    <section class="stats-grid"><div class="stat-card"><span class="stat-icon">₫</span><div><span class="stat-label">Doanh thu đã thanh toán</span><strong class="stat-num" id="summaryRevenue">—</strong></div></div><div class="stat-card"><span class="stat-icon"><svg class="icon"><use href="#icon-orders"/></svg></span><div><span class="stat-label">Tổng đơn hàng</span><strong class="stat-num" id="summaryOrders">—</strong></div></div><div class="stat-card"><span class="stat-icon">✓</span><div><span class="stat-label">Đơn hàng hoàn tất</span><strong class="stat-num" id="summaryCompleted">—</strong></div></div><div class="stat-card"><span class="stat-icon">↗</span><div><span class="stat-label">Giá trị đơn trung bình</span><strong class="stat-num" id="summaryAverage">—</strong></div></div></section>
    <section class="admin-card"><div class="card-header"><h2>Biểu đồ doanh thu</h2><span id="chartRange" class="text-muted"></span></div><div class="card-body"><div class="revenue-chart" id="revenueChart" aria-label="Biểu đồ doanh thu theo ngày"></div></div></section>
    <section class="admin-card"><div class="card-header"><h2>Doanh thu theo ngày</h2></div><div class="table-responsive"><table class="admin-table"><thead><tr><th>Ngày</th><th>Số đơn hàng</th><th>Doanh thu đã thanh toán</th></tr></thead><tbody id="dailyReport"><tr><td colspan="3" class="loading-state">Đang tải báo cáo...</td></tr></tbody></table></div></section>
    <section class="admin-card"><div class="card-header"><h2>Món ăn bán chạy</h2></div><div class="table-responsive"><table class="admin-table"><thead><tr><th>Món ăn</th><th>Số lượng bán</th><th>Giá trị món đã bán</th></tr></thead><tbody id="itemsReport"></tbody></table></div></section>
    <p class="report-note">Doanh thu gồm đơn đã hoàn tất và thanh toán thành công. Giá trị món đã bán tính trước giảm giá và phí giao hàng.</p>
</main>
<script src="<c:url value='/assets/js/admin-dashboard.js'/>"></script>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
