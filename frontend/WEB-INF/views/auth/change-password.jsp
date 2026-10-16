<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>
<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel"><p class="eyebrow">Bảo vệ tài khoản</p><h1>Mật khẩu mới, tài khoản an tâm.</h1>
        <p>Xác nhận mật khẩu hiện tại và mã gửi đến email của bạn trước khi thay đổi.</p></section>
    <section class="card auth-card" aria-labelledby="change-title">
        <div class="card-heading"><p class="eyebrow">Tài khoản Crave</p><h2 id="change-title">Đổi mật khẩu</h2>
            <p class="field-hint">Email xác nhận: <c:out value="${profile.email}"/></p></div>
        <%@ include file="../components/account-messages.jspf" %>
        <form id="changePasswordForm" action="<c:url value='/auth/change-password'/>" method="post">
            <label for="currentPassword">Mật khẩu hiện tại</label>
            <input id="currentPassword" name="currentPassword" type="password" autocomplete="current-password"
                   minlength="8" maxlength="72">
            <p class="field-hint">Nhập mật khẩu hiện tại khi yêu cầu gửi mã xác nhận.</p>
            <button class="button outline" type="submit" name="action" value="sendCode" formnovalidate>Gửi / gửi lại mã</button>
            <fieldset class="otp-fields">
                <legend>Xác nhận và tạo mật khẩu mới</legend>
                <label for="verificationCode">Mã xác nhận</label>
                <input id="verificationCode" name="verificationCode" class="otp-input" type="text" inputmode="numeric"
                       autocomplete="one-time-code" pattern="[0-9]{6}" minlength="6" maxlength="6" placeholder="000000" required>
                <p class="field-hint">Nhập 6 chữ số trong email. Mã có hiệu lực trong 10 phút; có thể gửi lại sau 60 giây.</p>
                <div class="field-grid">
                    <div><label for="password">Mật khẩu mới</label><input id="password" name="password" type="password"
                         autocomplete="new-password" minlength="8" maxlength="72" required>
                         <p class="field-hint">Mật khẩu có từ 8 đến 72 ký tự.</p></div>
                    <div><label for="confirmPassword">Nhập lại mật khẩu mới</label><input id="confirmPassword" name="confirmPassword"
                         type="password" autocomplete="new-password" minlength="8" maxlength="72" required></div>
                </div>
            </fieldset>
            <button class="button primary" type="submit" name="action" value="change">Đổi mật khẩu</button>
        </form>
        <p class="card-footnote">Sau khi đổi mật khẩu, bạn sẽ đăng nhập lại bằng mật khẩu mới.</p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
