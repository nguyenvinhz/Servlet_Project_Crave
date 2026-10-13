<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>

<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel">
        <p class="eyebrow">Thành viên mới</p>
        <h1>Bắt đầu những bữa ngon.</h1>
        <p>Điền thông tin và xác nhận email để lưu địa chỉ, theo dõi đơn hàng và nhận ưu đãi.</p>
    </section>

    <section class="card auth-card" aria-labelledby="register-title">
        <div class="card-heading">
            <p class="eyebrow">Bắt đầu với Crave</p>
            <h2 id="register-title">Đăng ký</h2>
        </div>

        <div id="form-error" class="form-error" role="alert" aria-live="polite" ${empty error ? 'hidden' : ''}>
            <c:out value="${error}"/>
        </div>

        <div id="form-success" class="form-success" role="status" aria-live="polite" hidden></div>

        <form id="registerForm" action="<c:url value='/api/auth/register'/>" method="post" data-method="POST"
              data-code-url="<c:url value='/api/auth/register/code'/>">
            <label for="fullName">Họ và tên</label>
            <input id="fullName" name="fullName" type="text" autocomplete="name"
                   minlength="2" maxlength="100" placeholder="Nguyễn Văn An" required>

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

            <div class="field-grid">
                <div>
                    <label for="password">Mật khẩu</label>
                    <input id="password" name="password" type="password" autocomplete="new-password"
                           minlength="8" maxlength="72" aria-describedby="password-hint" required>
                    <p id="password-hint" class="field-hint">Mật khẩu có từ 8 đến 72 ký tự.</p>
                </div>
                <div>
                    <label for="confirmPassword">Nhập lại mật khẩu</label>
                    <input id="confirmPassword" name="confirmPassword" type="password"
                           autocomplete="new-password" minlength="8" maxlength="72" required>
                </div>
            </div>

            <fieldset id="otp-fields" class="otp-fields" data-otp-fields hidden disabled>
                <legend>Xác nhận email</legend>
                <p id="otp-summary" class="field-hint"></p>
                <label for="verificationCode">Mã xác nhận</label>
                <input id="verificationCode" name="verificationCode" class="otp-input" type="text"
                       inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}"
                       minlength="6" maxlength="6" aria-describedby="otp-hint" placeholder="000000" required>
                <p id="otp-hint" class="field-hint">Nhập 6 chữ số trong email. Mã có hiệu lực trong 10 phút.</p>
                <div class="otp-actions">
                    <button id="otp-resend" class="button outline" type="button">Gửi lại mã</button>
                    <button id="otp-edit" class="button outline" type="button">Đổi email</button>
                </div>
            </fieldset>

            <button id="otp-submit" class="button primary" type="submit">Gửi mã xác nhận</button>
        </form>

        <p class="card-footnote">Đã có tài khoản?
            <a data-auth-switch href="<c:url value='/auth/login'/>">Đăng nhập</a>
        </p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
