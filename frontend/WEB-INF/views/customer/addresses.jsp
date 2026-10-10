<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="../components/header.jspf" %>
<main class="page account-page">
    <aside class="account-sidebar">
        <p class="eyebrow">Tài khoản</p>
        <h1>Thông tin của bạn</h1>
        <nav aria-label="Thiết lập tài khoản">
            <a href="<c:url value='/customer/profile'/>">Hồ sơ</a>
            <a class="active" href="<c:url value='/customer/addresses'/>">Địa chỉ giao hàng</a>
        </nav>
    </aside>

    <section class="account-content" aria-labelledby="address-title">
        <div class="card account-card">
            <div class="card-heading">
                <p class="eyebrow">Sổ địa chỉ</p>
                <h2 id="address-title">Địa chỉ giao hàng</h2>
                <p>Chọn địa chỉ mặc định để đặt món nhanh hơn.</p>
            </div>

            <div id="form-error" class="form-error" role="alert" aria-live="polite">
                <c:out value="${error}"/>
            </div>
            <div id="form-success" class="form-success" role="status" aria-live="polite">
                <c:out value="${message}"/>
            </div>

            <p id="address-loading" class="field-hint" role="status">Đang tải địa chỉ...</p>
            <div id="address-list" class="address-list" aria-label="Danh sách địa chỉ"></div>
            <div class="empty-state" id="address-empty-state" hidden>
                <span aria-hidden="true">⌂</span>
                <h3>Chưa có địa chỉ</h3>
                <p>Thêm địa chỉ đầu tiên bằng biểu mẫu bên dưới.</p>
            </div>
            <button id="address-refresh" class="address-action" type="button">Tải lại danh sách</button>
        </div>

        <div class="card account-card">
            <div class="card-heading">
                <p class="eyebrow">Thông tin giao hàng</p>
                <h2 id="address-form-title">Thêm địa chỉ</h2>
            </div>
            <form id="addressForm" action="<c:url value='/api/addresses'/>" method="post" data-method="POST">
                <fieldset id="address-fields" disabled>
                <legend class="visually-hidden">Thông tin địa chỉ</legend>
                <label for="addressLine">Địa chỉ đầy đủ</label>
                <textarea id="addressLine" name="addressLine" rows="3" maxlength="255"
                          placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành" required></textarea>

                <label for="note">Ghi chú giao hàng</label>
                <input id="note" name="note" type="text" maxlength="255"
                       placeholder="Ví dụ: gọi trước khi giao">

                <label class="checkbox-row" for="isDefault">
                    <input id="isDefault" name="isDefault" type="checkbox" value="true">
                    <span>Đặt làm địa chỉ mặc định</span>
                </label>

                <button id="address-submit" class="button primary" type="submit">Thêm địa chỉ</button>
                <button id="address-cancel" class="address-action cancel-edit" type="button" hidden>Hủy chỉnh sửa</button>
                </fieldset>
            </form>
        </div>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
