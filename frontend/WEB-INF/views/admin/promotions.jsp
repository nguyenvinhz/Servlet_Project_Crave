<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="adminPage" value="promotions" />
<c:set var="pageTitle" value="Quản lý khuyến mãi" />
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>
<link rel="stylesheet" href="<c:url value='/assets/css/admin-management.css?v=20261013-palette2' />">
<main class="admin-content">
    <header class="management-heading"><div><p class="management-eyebrow">Ưu đãi</p><h1>Quản lý khuyến mãi</h1>
        <p>Tạo chương trình giảm giá và theo dõi thời gian áp dụng cho khách hàng.</p></div>
        <div class="management-actions"><button type="button" class="button outline" id="managementRefresh">Tải lại</button>
            <button type="button" class="button primary" id="promotionCreate">＋ Thêm khuyến mãi</button></div></header>
    <div id="managementFeedback" class="management-feedback" role="status" aria-live="polite" data-tone="${not empty sessionScope.adminError ? 'error' : 'success'}" ${empty sessionScope.adminError and empty sessionScope.adminSuccess ? 'hidden' : ''}><c:out value="${not empty sessionScope.adminError ? sessionScope.adminError : sessionScope.adminSuccess}"/></div><c:remove var="adminError" scope="session"/><c:remove var="adminSuccess" scope="session"/>
    <div class="admin-toolbar management-toolbar"><label class="management-search"><span class="sr-only">Tìm khuyến mãi</span>
        <input id="managementSearch" type="search" placeholder="Tìm theo tên hoặc mã khuyến mãi…" autocomplete="off"></label>
        <label><span class="sr-only">Lọc trạng thái</span><select id="managementStatus"><option value="">Mọi trạng thái</option>
            <option value="CURRENT">Đang áp dụng</option><option value="UPCOMING">Sắp diễn ra</option><option value="EXPIRED">Hết hạn</option>
            <option value="INACTIVE">Đã tắt</option></select></label><span id="managementCount" class="management-count" aria-live="polite"></span></div>
    <section id="managementPromotionList" class="management-promotion-list" aria-label="Danh sách khuyến mãi"><c:forEach var="item" items="${promotions}"><article class="management-promo-card" data-promotion
 data-id="<c:out value='${item.promotionId}'/>" data-name="<c:out value='${item.name}'/>" data-code="<c:out value='${item.code}'/>" data-discount-type="${item.discountType}" data-discount-value="${item.discountValue}" data-minimum-order-value="${item.minimumOrderValue}" data-maximum-discount="${item.maximumDiscount}" data-start-at="${item.startAt}" data-end-at="${item.endAt}" data-status="${item.status}">
<div class="management-promo-value"><span><c:out value="${item.code}"/></span><strong>−<fmt:formatNumber value="${item.discountValue}" groupingUsed="true"/>${item.discountType == 'PERCENT' ? '%' : ' ₫'}</strong></div>
<div class="management-promo-body"><div class="management-promo-top"><h2><c:out value="${item.name}"/></h2><span class="management-badge" data-promo-state></span></div>
<p class="management-promo-dates"><span data-date="${item.startAt}" data-time></span> – <span data-date="${item.endAt}" data-time></span></p>
<p class="management-promo-rules">Đơn từ <fmt:formatNumber value="${item.minimumOrderValue}" groupingUsed="true"/> ₫<c:if test="${not empty item.maximumDiscount}"> · Giảm tối đa <fmt:formatNumber value="${item.maximumDiscount}" groupingUsed="true"/> ₫</c:if></p>
<div class="management-promo-progress"><span></span></div><div class="management-promo-actions"><button type="button" class="button outline" data-promotion-edit>Chỉnh sửa</button><button type="button" class="button outline" data-status-change>${item.status == 'ACTIVE' ? 'Tắt áp dụng' : 'Bật áp dụng'}</button></div></div></article></c:forEach>
<c:if test="${empty promotions}"><div class="admin-card management-empty management-no-results">Không tìm thấy chương trình khuyến mãi phù hợp.</div></c:if></section>
    <dialog id="promotionDialog" class="modal-dialog admin-management-dialog" aria-labelledby="promotionDialogTitle">
        <form id="promotionForm" method="post" action="<c:url value='/admin/promotions/action'/>"><input type="hidden" name="action" value="create"><input type="hidden" name="id"><div class="modal-header"><h2 id="promotionDialogTitle">Thêm khuyến mãi</h2>
            <button type="button" class="management-close" data-close-dialog aria-label="Đóng">×</button></div>
            <div class="modal-body management-form"><div class="form-row"><div class="form-group"><label for="promotionCode">Mã khuyến mãi</label>
                <input id="promotionCode" name="code" required maxlength="30" pattern="[A-Za-z0-9_-]+" placeholder="WELCOME10"></div>
                <div class="form-group"><label for="promotionStatus">Trạng thái</label><select id="promotionStatus" name="status">
                    <option value="ACTIVE">Bật áp dụng</option><option value="INACTIVE">Tắt áp dụng</option></select></div></div>
                <div class="form-group"><label for="promotionName">Tên chương trình</label><input id="promotionName" name="name" required maxlength="150"></div>
                <div class="form-row"><div class="form-group"><label for="promotionType">Loại giảm giá</label><select id="promotionType" name="discountType">
                    <option value="PERCENT">Theo phần trăm</option><option value="FIXED_AMOUNT">Theo số tiền</option></select></div>
                    <div class="form-group"><label for="promotionValue" id="promotionValueLabel">Giảm giá (%)</label>
                        <input id="promotionValue" name="discountValue" type="number" min="1" max="100" step="1" required></div></div>
                <div class="form-row"><div class="form-group"><label for="promotionMinimum">Đơn tối thiểu (đ)</label>
                    <input id="promotionMinimum" name="minimumOrderValue" type="number" min="0" max="999999999999" step="1" value="0" required></div>
                    <div class="form-group"><label for="promotionMaximum">Giảm tối đa (đ)</label>
                        <input id="promotionMaximum" name="maximumDiscount" type="number" min="1" max="999999999999" step="1" placeholder="Không giới hạn"></div></div>
                <div class="form-row"><div class="form-group"><label for="promotionStart">Bắt đầu</label>
                    <input id="promotionStart" name="startAt" type="datetime-local" step="60" required></div>
                    <div class="form-group"><label for="promotionEnd">Kết thúc</label><input id="promotionEnd" name="endAt" type="datetime-local" step="60" required></div></div>
                <small>Thời gian áp dụng theo múi giờ Việt Nam.</small><p id="promotionFormError" class="management-form-error" role="alert" hidden></p></div>
            <div class="modal-footer"><button type="button" class="button outline" data-close-dialog>Hủy</button><button type="submit" class="button primary">Lưu khuyến mãi</button></div></form>
    </dialog>
    <dialog id="managementConfirm" class="modal-dialog admin-management-dialog" aria-labelledby="managementConfirmTitle">
        <form id="managementConfirmForm" method="post" action="<c:url value='/admin/promotions/action'/>"><input type="hidden" name="action" value="status"><input type="hidden" name="id"><input type="hidden" name="status"><div class="modal-header"><h2 id="managementConfirmTitle">Cập nhật khuyến mãi</h2>
            <button type="button" class="management-close" data-close-dialog aria-label="Đóng">×</button></div>
            <div class="modal-body"><p id="managementConfirmMessage"></p></div>
            <div class="modal-footer"><button type="button" class="button outline" data-close-dialog>Hủy</button>
                <button type="submit" class="button primary" id="managementConfirmSubmit">Xác nhận</button></div></form>
    </dialog>
</main>
<script src="<c:url value='/assets/js/admin-management.js?v=20261017-forms1' />" defer></script>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
