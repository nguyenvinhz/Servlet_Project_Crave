<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>
<main id="main-content" class="page account-page account-ui">
    <aside class="account-sidebar"><p class="eyebrow">Tài khoản</p><h1>Thông tin của bạn</h1>
        <nav aria-label="Thiết lập tài khoản">
            <a class="active" aria-current="page" href="<c:url value='/customer/profile'/>">Hồ sơ</a>
            <a href="<c:url value='/auth/change-password'/>">Đổi mật khẩu</a>
            <c:if test="${sessionScope.currentUser.accountType == 'CUSTOMER'}"><a href="<c:url value='/customer/addresses'/>">Địa chỉ giao hàng</a></c:if>
        </nav>
    </aside>
    <section class="card account-card" aria-labelledby="profile-title">
        <div class="card-heading"><p class="eyebrow">Thông tin cá nhân</p><h2 id="profile-title">Hồ sơ</h2>
            <p>Cập nhật thông tin dùng cho xác nhận và giao đơn.</p></div>
        <%@ include file="../components/account-messages.jspf" %>
        <c:if test="${profile.accountType == 'EMPLOYEE'}"><p class="field-hint">Hồ sơ nhân viên chỉ có thể xem tại đây.</p></c:if>
        <form id="profileForm" action="<c:url value='/customer/profile'/>" method="post">
            <fieldset ${profile.accountType == 'EMPLOYEE' ? 'disabled' : ''}>
                <legend class="visually-hidden">Thông tin hồ sơ</legend>
                <label for="fullName">Họ và tên</label>
                <input id="fullName" name="fullName" type="text" autocomplete="name" minlength="2" maxlength="100"
                       value="<c:out value='${submitted ? param.fullName : profile.fullName}'/>" required>
                <div class="field-grid">
                    <div><label for="email">Email</label><input id="email" name="email" type="email" autocomplete="email"
                         maxlength="100" value="<c:out value='${submitted ? param.email : profile.email}'/>" required></div>
                    <div><label for="phone">Số điện thoại</label><input id="phone" name="phone" type="tel" autocomplete="tel"
                         minlength="9" maxlength="15" pattern="\+?[0-9]{8,15}" inputmode="tel"
                         value="<c:out value='${submitted ? param.phone : profile.phone}'/>" required></div>
                </div>
                <button class="button primary" type="submit">Lưu thay đổi</button>
            </fieldset>
        </form>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
