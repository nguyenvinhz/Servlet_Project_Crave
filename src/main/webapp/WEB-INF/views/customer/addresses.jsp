<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>

<main id="main-content" class="page account-page account-ui">
    <aside class="account-sidebar">
        <p class="eyebrow">Tài khoản</p>
        <h1>Thông tin của bạn</h1>
        <nav aria-label="Thiết lập tài khoản">
            <a href="<c:url value='/customer/profile'/>">Hồ sơ</a>
            <a class="active" aria-current="page" href="<c:url value='/customer/addresses'/>">Địa chỉ giao hàng</a>
        </nav>
    </aside>

    <section class="account-content" aria-labelledby="address-title">
        <div class="card account-card">
            <div class="card-heading">
                <p class="eyebrow">Sổ địa chỉ</p>
                <h2 id="address-title">Địa chỉ giao hàng</h2>
                <p>Chọn địa chỉ mặc định để đặt món nhanh hơn.</p>
            </div>

            <div id="form-error" class="form-error" role="alert" aria-live="polite" ${empty error ? 'hidden' : ''}>
                <c:out value="${error}"/><c:forEach var="fieldError" items="${fieldErrors}"><p class="field-hint"><c:out value="${fieldError.value}"/></p></c:forEach>
            </div>
            <div id="form-success" class="form-success" role="status" aria-live="polite" ${empty message ? 'hidden' : ''}>
                <c:out value="${message}"/>
            </div>

            <p id="address-loading" class="field-hint" role="status" hidden>Đang tải địa chỉ...</p>
            <div id="address-list" class="address-list" aria-label="Danh sách địa chỉ">
                <c:forEach var="entry" items="${addresses}">
                    <article class="address-item">
                        <h3><c:out value="${entry.addressLine}"/></h3>
                        <c:if test="${entry.isDefault}"><span class="address-badge">Mặc định</span></c:if>
                        <c:if test="${not empty entry.note}"><p><c:out value="${entry.note}"/></p></c:if>
                        <div class="address-actions">
                            <c:url var="editAddressUrl" value="/customer/addresses"><c:param name="edit" value="${entry.id}"/></c:url>
                            <button class="address-action" type="button" data-location="<c:out value='${editAddressUrl}'/>#addressForm">Sửa</button>
                            <c:if test="${not entry.isDefault}">
                                <form action="<c:url value='/customer/addresses/default'/>" method="post" style="margin: 0;">
                                    <input type="hidden" name="addressId" value="<c:out value='${entry.id}'/>">
                                    <button class="address-action" type="submit">Đặt mặc định</button>
                                </form>
                            </c:if>
                            <form action="<c:url value='/customer/addresses/delete'/>" method="post" style="margin: 0;" data-confirm="Bạn muốn xóa địa chỉ này?">
                                <input type="hidden" name="addressId" value="<c:out value='${entry.id}'/>">
                                <button class="address-action danger" type="submit">Xóa</button>
                            </form>
                        </div>
                    </article>
                </c:forEach>
            </div>
            <div class="empty-state" id="address-empty-state" ${empty addresses ? '' : 'hidden'}>
                <span aria-hidden="true">⌂</span>
                <h3>Chưa có địa chỉ</h3>
                <p>Thêm địa chỉ đầu tiên bằng biểu mẫu bên dưới.</p>
            </div>
            <button id="address-refresh" class="address-action" type="button" data-location="<c:url value='/customer/addresses'/>">Tải lại danh sách</button>
        </div>

        <div class="card account-card">
            <div class="card-heading">
                <p class="eyebrow">Thông tin giao hàng</p>
                <h2 id="address-form-title">${empty editing ? 'Thêm địa chỉ' : 'Sửa địa chỉ'}</h2>
            </div>
            <form id="addressForm" action="<c:url value='/customer/addresses/save'/>" method="post">
                <input type="hidden" name="addressId" value="<c:out value='${editing}'/>">
                <fieldset id="address-fields">
                <legend class="visually-hidden">Thông tin địa chỉ</legend>
                <label for="addressLine">Địa chỉ đầy đủ</label>
                <textarea id="addressLine" name="addressLine" rows="3" maxlength="255"
                          placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành" required><c:out value="${not empty error ? param.addressLine : address.addressLine}"/></textarea>

                <label for="note">Ghi chú giao hàng</label>
                <input id="note" name="note" type="text" value="<c:out value='${not empty error ? param.note : address.note}'/>" maxlength="255"
                       placeholder="Ví dụ: gọi trước khi giao">

                <label class="checkbox-row" for="isDefault">
                    <input id="isDefault" name="isDefault" type="checkbox" value="true" ${(not empty error ? param.isDefault == 'true' : address.isDefault) ? 'checked' : ''}>
                    <span>Đặt làm địa chỉ mặc định</span>
                </label>

                <button id="address-submit" class="button primary" type="submit">${empty editing ? 'Thêm địa chỉ' : 'Lưu địa chỉ'}</button>
                <button id="address-cancel" class="address-action cancel-edit" type="button" data-location="<c:url value='/customer/addresses'/>" ${empty editing ? 'hidden' : ''}>Hủy chỉnh sửa</button>
                </fieldset>
            </form>
        </div>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
