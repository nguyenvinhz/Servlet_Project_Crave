<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>
<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel"><p class="eyebrow">Khôi phục tài khoản</p><h1>Lấy lại mật khẩu, tiếp tục bữa ngon.</h1>
        <p>Dùng email đã đăng ký để nhận mã xác nhận. Áp dụng cho khách hàng, nhân viên và quản trị viên.</p></section>
    <section class="card auth-card" aria-labelledby="reset-title">
        <div class="card-heading"><p class="eyebrow">Tài khoản Crave</p><h2 id="reset-title">Quên mật khẩu</h2>
            <p>Chúng tôi sẽ gửi mã nếu email thuộc tài khoản đang hoạt động.</p></div>
        <%@ include file="../components/account-messages.jspf" %>
        <form id="resetPasswordForm" action="<c:url value='/auth/forgot-password'/>" method="post">
            <input type="hidden" name="returnTo" value="<c:out value='${param.returnTo}'/>">
            <label for="email">Email tài khoản</label>
            <input id="email" name="email" type="email" autocomplete="email" maxlength="100"
                   value="<c:out value='${param.email}'/>" placeholder="ban@example.com" required>
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
            <button class="button primary" type="submit" name="action" value="reset">Đặt lại mật khẩu</button>
        </form>
        <c:url var="loginUrl" value="/auth/login"><c:param name="returnTo" value="${param.returnTo}"/></c:url>
        <p class="card-footnote"><a href="<c:out value='${loginUrl}'/>">Quay lại đăng nhập</a></p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
