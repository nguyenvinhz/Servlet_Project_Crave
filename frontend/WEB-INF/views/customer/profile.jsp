<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="../components/header.jspf" %>
<main class="page account-page">
    <aside class="account-sidebar">
        <p class="eyebrow">Tài khoản</p>
        <h1>Thông tin của bạn</h1>
        <nav aria-label="Thiết lập tài khoản">
            <a class="active" href="<c:url value='/customer/profile'/>">Hồ sơ</a>
            <c:if test="${sessionScope.currentUser.accountType == 'CUSTOMER'}">
                <a href="<c:url value='/customer/addresses'/>">Địa chỉ giao hàng</a>
            </c:if>
        </nav>
    </aside>

    <section class="card account-card" aria-labelledby="profile-title">
        <div class="card-heading">
            <p class="eyebrow">Thông tin cá nhân</p>
            <h2 id="profile-title">Hồ sơ</h2>
            <p>Cập nhật thông tin dùng cho xác nhận và giao đơn.</p>
        </div>

        <div id="form-error" class="form-error" role="alert" aria-live="polite">
            <c:out value="${error}"/>
        </div>
        <div id="form-success" class="form-success" role="status" aria-live="polite">
            <c:out value="${message}"/>
        </div>
        <p id="profile-loading" class="field-hint" role="status">Đang tải hồ sơ...</p>
        <p id="profile-readonly" class="field-hint" hidden>Hồ sơ nhân viên chỉ có thể xem tại đây.</p>
        <button id="profile-retry" class="address-action" type="button" hidden>Thử tải lại</button>

        <form id="profileForm" action="<c:url value='/api/profile'/>" method="post" data-method="PUT">
            <fieldset id="profile-fields" disabled>
            <legend class="visually-hidden">Thông tin hồ sơ</legend>
            <label for="fullName">Họ và tên</label>
            <input id="fullName" name="fullName" type="text" autocomplete="name"
                   minlength="2" maxlength="100" placeholder="Họ và tên" required>

            <div class="field-grid">
                <div>
                    <label for="email">Email</label>
                    <input id="email" name="email" type="email" autocomplete="email"
                           maxlength="100" placeholder="ban@example.com" required>
                </div>
                <div>
                    <label for="phone">Số điện thoại</label>
                    <input id="phone" name="phone" type="tel" autocomplete="tel"
                           minlength="9" maxlength="15" pattern="\+?[0-9]{8,15}" inputmode="tel" placeholder="0901234567" required>
                </div>
            </div>

            <button class="button primary" type="submit">Lưu thay đổi</button>
            </fieldset>
        </form>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
