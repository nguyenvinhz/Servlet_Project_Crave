<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>
<main id="main-content" class="page account-page account-ui">
    <aside class="account-sidebar"><p class="eyebrow">Tài khoản</p><h1>Thông tin của bạn</h1>
        <nav aria-label="Thiết lập tài khoản"><a href="<c:url value='/customer/profile'/>">Hồ sơ</a>
            <a class="active" aria-current="page" href="<c:url value='/customer/addresses'/>">Địa chỉ giao hàng</a></nav>
    </aside>
    <section class="account-content" aria-labelledby="address-title">
        <div class="card account-card">
            <div class="card-heading"><p class="eyebrow">Sổ địa chỉ</p><h2 id="address-title">Địa chỉ giao hàng</h2>
                <p>Chọn địa chỉ mặc định để đặt món nhanh hơn.</p></div>
            <%@ include file="../components/account-messages.jspf" %>
            <div class="address-list" aria-label="Danh sách địa chỉ">
                <c:forEach var="address" items="${addresses}">
                    <article class="address-item">
                        <h3><c:out value="${address.addressLine}"/></h3>
                        <c:if test="${address.defaultAddress}"><span class="address-badge">Mặc định</span></c:if>
                        <c:if test="${not empty address.note}"><p><c:out value="${address.note}"/></p></c:if>
                        <div class="address-actions">
                            <c:url var="editUrl" value="/customer/addresses"><c:param name="edit" value="${address.id}"/></c:url>
                            <a class="address-action" href="<c:out value='${editUrl}'/>">Sửa</a>
                            <c:if test="${not address.defaultAddress}">
                                <form action="<c:url value='/customer/addresses'/>" method="post">
                                    <input type="hidden" name="id" value="<c:out value='${address.id}'/>">
                                    <button class="address-action" name="action" value="default" type="submit">Đặt mặc định</button>
                                </form>
                            </c:if>
                            <form action="<c:url value='/customer/addresses'/>" method="post" data-confirm="Bạn muốn xóa địa chỉ này?">
                                <input type="hidden" name="id" value="<c:out value='${address.id}'/>">
                                <button class="address-action danger" name="action" value="delete" type="submit">Xóa</button>
                            </form>
                        </div>
                    </article>
                </c:forEach>
            </div>
            <c:if test="${empty addresses}"><div class="empty-state"><span aria-hidden="true">⌂</span><h3>Chưa có địa chỉ</h3>
                <p>Thêm địa chỉ đầu tiên bằng biểu mẫu bên dưới.</p></div></c:if>
            <a class="address-action" href="<c:url value='/customer/addresses'/>">Tải lại danh sách</a>
        </div>
        <c:set var="addressId" value="${submitted ? editingId : editing.id}"/>
        <div class="card account-card">
            <div class="card-heading"><p class="eyebrow">Thông tin giao hàng</p><h2>${empty addressId ? 'Thêm địa chỉ' : 'Sửa địa chỉ'}</h2></div>
            <form id="addressForm" action="<c:url value='/customer/addresses'/>" method="post">
                <input type="hidden" name="action" value="save">
                <input type="hidden" name="id" value="<c:out value='${addressId}'/>">
                <label for="addressLine">Địa chỉ đầy đủ</label>
                <textarea id="addressLine" name="addressLine" rows="3" maxlength="255"
                          placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành" required><c:out value="${submitted ? param.addressLine : editing.addressLine}"/></textarea>
                <label for="note">Ghi chú giao hàng</label>
                <input id="note" name="note" type="text" maxlength="255" value="<c:out value='${submitted ? param.note : editing.note}'/>"
                       placeholder="Ví dụ: gọi trước khi giao">
                <label class="checkbox-row" for="isDefault"><input id="isDefault" name="isDefault" type="checkbox" value="true"
                    ${(submitted ? param.isDefault == 'true' : editing.defaultAddress) ? 'checked' : ''}><span>Đặt làm địa chỉ mặc định</span></label>
                <button class="button primary" type="submit">${empty addressId ? 'Thêm địa chỉ' : 'Lưu địa chỉ'}</button>
                <c:if test="${not empty addressId}"><a class="address-action cancel-edit" href="<c:url value='/customer/addresses'/>">Hủy chỉnh sửa</a></c:if>
            </form>
        </div>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
