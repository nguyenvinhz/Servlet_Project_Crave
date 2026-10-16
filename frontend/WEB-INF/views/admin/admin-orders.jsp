<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN"/>
<c:set var="pageTitle" value="Đơn hàng"/><c:set var="adminPage" value="orders"/>
<%@ include file="/WEB-INF/views/admin/components/begin.jspf" %>
<main class="admin-content">
<header class="page-header"><div><h1>Quản lý đơn hàng</h1><p>Theo dõi và cập nhật đơn hàng, thanh toán.</p></div><a class="button outline" href="<c:url value='/admin/orders'/>">Tải lại</a></header>
<%@ include file="/WEB-INF/views/admin/components/feedback.jspf" %>
<c:if test="${not empty flashError}"><p class="alert" role="alert"><c:out value="${flashError}"/></p></c:if>
<c:if test="${not empty flashMessage}"><p class="alert alert-success" role="status"><c:out value="${flashMessage}"/></p></c:if>
<form method="get" action="<c:url value='/admin/orders'/>" class="admin-toolbar management-toolbar">
<input type="search" name="search" placeholder="Mã đơn, người nhận, điện thoại" value="<c:out value='${param.search}'/>" aria-label="Tìm đơn hàng">
<select name="status" aria-label="Trạng thái đơn"><option value="">Mọi trạng thái</option><c:forEach var="status" items="${orderStatuses}"><option value="${status}" ${param.status == status ? 'selected' : ''}><c:out value="${adminLabels[status.toString()]}"/></option></c:forEach></select>
<button class="button primary">Lọc đơn</button><a class="button outline" href="<c:url value='/admin/orders'/>">Đặt lại</a>
</form>
<section class="admin-card management-table-wrap"><table class="admin-table"><thead><tr><th>Đơn hàng</th><th>Người nhận</th><th>Tổng tiền</th><th>Trạng thái</th><th>Thanh toán</th><th>Thao tác</th></tr></thead><tbody>
<c:forEach var="order" items="${orders}">
<tr><td><a href="<c:url value='/admin/orders'><c:param name='id' value='${order.orderId}'/></c:url>#order-detail"><strong><c:out value="${order.orderId}"/></strong></a><br><small><c:out value="${order.orderedAt}"/></small><br><c:out value="${adminLabels[order.fulfillmentType.toString()]}"/></td>
<td><c:out value="${order.receiverName}"/><br><c:out value="${order.receiverPhone}"/></td>
<td><fmt:formatNumber value="${order.totalAmount}"/> ₫</td><td><c:out value="${adminLabels[order.status.toString()]}"/></td>
<td><c:if test="${not empty order.payment}"><c:out value="${adminLabels[order.payment.method]}"/><br><c:out value="${adminLabels[order.payment.status]}"/></c:if></td>
<td>
<c:if test="${(order.status != 'COMPLETED' and order.status != 'CANCELLED') or (not empty order.payment and order.payment.status != 'REFUNDED')}">
<details class="order-row-actions"><summary class="button outline">Cập nhật</summary>
<c:if test="${order.status != 'COMPLETED' and order.status != 'CANCELLED'}">
<form method="post" action="<c:url value='/admin/orders'/>" class="management-form">
<input type="hidden" name="orderId" value="<c:out value='${order.orderId}'/>">
<div class="form-group"><label for="status-${order.orderId}">Trạng thái đơn</label><select id="status-${order.orderId}" name="status">
<c:forEach var="status" items="${orderStatuses}">
<c:if test="${order.status == status or (order.status == 'PENDING_CONFIRMATION' and (status == 'PREPARING' or status == 'CANCELLED')) or (order.status == 'PREPARING' and (status == 'CANCELLED' or (order.fulfillmentType == 'PICKUP' and status == 'COMPLETED') or (order.fulfillmentType == 'DELIVERY' and status == 'DELIVERING'))) or (order.status == 'DELIVERING' and status == 'COMPLETED')}">
<option value="${status}" ${order.status == status ? 'selected' : ''}><c:out value="${adminLabels[status.toString()]}"/></option>
</c:if></c:forEach></select></div>
<div class="form-group"><label for="note-${order.orderId}">Ghi chú xử lý</label><input id="note-${order.orderId}" name="note" maxlength="500" placeholder="Ghi chú"></div>
<button class="button outline">Cập nhật đơn</button></form>
</c:if>
<c:if test="${not empty order.payment and order.payment.status != 'REFUNDED'}">
<form method="post" action="<c:url value='/admin/payments'/>" class="management-form">
<input type="hidden" name="paymentId" value="<c:out value='${order.payment.paymentId}'/>">
<div class="form-group"><label for="payment-${order.orderId}">Trạng thái thanh toán</label><select id="payment-${order.orderId}" name="status">
<c:forEach var="paymentStatus" items="${['PENDING','SUCCESS','FAILED','REFUNDED']}">
<c:if test="${order.payment.status == paymentStatus or (order.payment.status == 'PENDING' and (paymentStatus == 'FAILED' or (paymentStatus == 'SUCCESS' and order.status != 'CANCELLED'))) or (order.payment.status == 'FAILED' and (paymentStatus == 'PENDING' or (paymentStatus == 'SUCCESS' and order.status != 'CANCELLED'))) or (order.payment.status == 'SUCCESS' and paymentStatus == 'REFUNDED')}">
<option value="${paymentStatus}" ${order.payment.status == paymentStatus ? 'selected' : ''}><c:out value="${adminLabels[paymentStatus]}"/></option>
</c:if></c:forEach></select></div>
<div class="form-group"><label for="reference-${order.orderId}">Mã giao dịch</label><input id="reference-${order.orderId}" name="transactionRef" maxlength="100" placeholder="Mã giao dịch"></div>
<button class="button outline">Cập nhật thanh toán</button></form>
</c:if>
</details></c:if>
</td></tr></c:forEach>
<c:if test="${empty orders}"><tr><td colspan="6" class="management-empty">Không tìm thấy đơn hàng phù hợp.</td></tr></c:if>
</tbody></table></section>
<c:if test="${not empty selectedOrder}">
<section class="admin-card" id="order-detail"><div class="card-header"><h2>Chi tiết <c:out value="${selectedOrder.orderId}"/></h2><span><c:out value="${adminLabels[selectedOrder.status.toString()]}"/></span></div><div class="card-body">
<p><strong>Người nhận:</strong> <c:out value="${selectedOrder.receiverName}"/> · <c:out value="${selectedOrder.receiverPhone}"/></p>
<p><strong>Hình thức:</strong> <c:out value="${adminLabels[selectedOrder.fulfillmentType.toString()]}"/></p>
<c:if test="${not empty selectedOrder.deliveryAddress}"><p><strong>Địa chỉ:</strong> <c:out value="${selectedOrder.deliveryAddress}"/></p></c:if>
<c:if test="${not empty selectedOrder.customerNote}"><p><strong>Ghi chú khách:</strong> <c:out value="${selectedOrder.customerNote}"/></p></c:if>
<div class="table-responsive"><table class="admin-table"><thead><tr><th>Món ăn</th><th>Số lượng</th><th>Thành tiền</th></tr></thead><tbody>
<c:forEach var="item" items="${selectedOrder.items}"><tr><td><strong><c:out value="${item.foodNameSnapshot}"/></strong><c:forEach var="option" items="${item.options}"><p><c:out value="${option.optionName}"/></p></c:forEach><c:if test="${not empty item.note}"><p><c:out value="${item.note}"/></p></c:if></td><td>${item.quantity}</td><td><fmt:formatNumber value="${item.lineTotal}"/> ₫</td></tr></c:forEach></tbody></table></div>
<p>Tiền món: <fmt:formatNumber value="${selectedOrder.subtotal}"/> ₫ · Giảm giá: <fmt:formatNumber value="${selectedOrder.discountAmount}"/> ₫ · Giao hàng: <fmt:formatNumber value="${selectedOrder.deliveryFee}"/> ₫</p>
<p><strong>Tổng tiền: <fmt:formatNumber value="${selectedOrder.totalAmount}"/> ₫</strong></p>
<h3>Lịch sử xử lý</h3>
<c:forEach var="entry" items="${selectedOrder.history}"><p><c:out value="${entry.changedAt}"/> · <c:out value="${adminLabels[entry.status]}"/> · <c:out value="${entry.note}"/></p></c:forEach>
</div></section></c:if>
</main>
<%@ include file="/WEB-INF/views/admin/components/end.jspf" %>
