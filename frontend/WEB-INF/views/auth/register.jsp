<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>
<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel">
        <p class="eyebrow">Thành viên mới</p><h1>Bắt đầu những bữa ngon.</h1>
        <p>Điền thông tin và xác nhận email để lưu địa chỉ, theo dõi đơn hàng và nhận ưu đãi.</p>
    </section>
    <section class="card auth-card" aria-labelledby="register-title">
        <div class="card-heading"><p class="eyebrow">Bắt đầu với Crave</p><h2 id="register-title">Đăng ký</h2></div>
        <%@ include file="../components/account-messages.jspf" %>
        <form id="registerForm" action="<c:url value='/auth/register'/>" method="post">
            <input type="hidden" name="returnTo" value="<c:out value='${param.returnTo}'/>">
            <label for="fullName">Họ và tên</label>
            <input id="fullName" name="fullName" type="text" autocomplete="name" minlength="2" maxlength="100"
                   value="<c:out value='${param.fullName}'/>" placeholder="Nguyễn Văn An" required>
            <div class="field-grid">
                <div><label for="email">Email</label><input id="email" name="email" type="email" autocomplete="email"
                     maxlength="100" value="<c:out value='${param.email}'/>" placeholder="ban@example.com" required></div>
                <div><label for="phone">Số điện thoại</label><input id="phone" name="phone" type="tel" autocomplete="tel"
                     minlength="9" maxlength="15" pattern="\+?[0-9]{8,15}" inputmode="tel"
                     value="<c:out value='${param.phone}'/>" placeholder="0901234567" required></div>
            </div>
            <fieldset class="otp-fields">
                <legend>Xác nhận email</legend>
                <p class="field-hint">Gửi mã đến email ở trên trước khi hoàn tất đăng ký. Bạn có thể gửi lại sau 60 giây.</p>
                <button class="button outline" type="submit" name="action" value="sendCode" formnovalidate>Gửi / gửi lại mã</button>
                <label for="verificationCode">Mã xác nhận</label>
                <input id="verificationCode" name="verificationCode" class="otp-input" type="text" inputmode="numeric"
                       autocomplete="one-time-code" pattern="[0-9]{6}" minlength="6" maxlength="6" placeholder="000000" required>
                <p class="field-hint">Nhập 6 chữ số trong email. Mã có hiệu lực trong 10 phút.</p>
            </fieldset>
            <div class="field-grid">
                <div><label for="password">Mật khẩu</label><input id="password" name="password" type="password"
                     autocomplete="new-password" minlength="8" maxlength="72" required>
                     <p class="field-hint">Mật khẩu có từ 8 đến 72 ký tự.</p></div>
                <div><label for="confirmPassword">Nhập lại mật khẩu</label><input id="confirmPassword" name="confirmPassword"
                     type="password" autocomplete="new-password" minlength="8" maxlength="72" required></div>
            </div>
            <button class="button primary" type="submit" name="action" value="register">Đăng ký</button>
        </form>
        <c:url var="loginUrl" value="/auth/login"><c:param name="returnTo" value="${param.returnTo}"/></c:url>
        <p class="card-footnote">Đã có tài khoản? <a href="<c:out value='${loginUrl}'/>">Đăng nhập</a></p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
