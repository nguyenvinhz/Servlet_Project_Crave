<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>

<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel">
        <p class="eyebrow">Khôi phục tài khoản</p>
        <h1>Lấy lại mật khẩu, tiếp tục bữa ngon.</h1>
        <p>Dùng email đã đăng ký để nhận mã xác nhận. Áp dụng cho khách hàng, nhân viên và quản trị viên.</p>
    </section>
    <section class="card auth-card" aria-labelledby="reset-title">
        <div class="card-heading">
            <p class="eyebrow">Tài khoản Crave</p>
            <h2 id="reset-title">Quên mật khẩu</h2>
            <p>Chúng tôi sẽ gửi mã nếu email thuộc tài khoản đang hoạt động.</p>
        </div>
        <div id="form-error" class="form-error" role="alert" aria-live="polite" ${empty error ? 'hidden' : ''}><c:out value="${error}"/><c:forEach var="fieldError" items="${fieldErrors}"><p class="field-hint"><c:out value="${fieldError.value}"/></p></c:forEach></div>
        <div id="form-success" class="form-success" role="status" aria-live="polite" ${empty message ? 'hidden' : ''}><c:out value="${message}"/></div>
        <form id="resetPasswordForm" action="<c:url value='${otpRequested ? "/auth/password/reset/submit" : "/auth/password/reset/code"}'/>" method="post"
              data-code-url="<c:url value='/auth/password/reset/code'/>">
            <input type="hidden" name="otpActive" value="${otpRequested ? 'true' : 'false'}">
            <label for="email">Email tài khoản</label>
            <input id="email" name="email" type="email" value="<c:out value='${param.email}'/>" autocomplete="email" maxlength="100"
                   placeholder="ban@example.com" required>
            <fieldset id="otp-fields" class="otp-fields" data-otp-fields ${otpRequested ? '' : 'hidden disabled'}>
                <legend>Xác nhận và tạo mật khẩu mới</legend>
                <p id="otp-summary" class="field-hint">Nhập mã trong email để tiếp tục.</p>
                <label for="verificationCode">Mã xác nhận</label>
                <input id="verificationCode" name="verificationCode" class="otp-input" type="text"
                       inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" minlength="6"
                       maxlength="6" aria-describedby="otp-hint" placeholder="000000" required>
                <p id="otp-hint" class="field-hint">Nhập 6 chữ số trong email. Mã có hiệu lực trong 10 phút.</p>
                <div class="field-grid">
                    <div>
                        <label for="password">Mật khẩu mới</label>
                        <input id="password" name="password" type="password" autocomplete="new-password"
                               minlength="8" maxlength="72" aria-describedby="password-hint" required>
                        <p id="password-hint" class="field-hint">Mật khẩu có từ 8 đến 72 ký tự.</p>
                    </div>
                    <div>
                        <label for="confirmPassword">Nhập lại mật khẩu mới</label>
                        <input id="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password"
                               minlength="8" maxlength="72" required>
                    </div>
                </div>
                <div class="otp-actions">
                    <button id="otp-resend" class="button outline" type="submit" formaction="<c:url value='/auth/password/reset/code'/>" formnovalidate>Gửi lại mã</button>
                    <button id="otp-edit" class="button outline" type="button">Đổi email</button>
                </div>
            </fieldset>
            <button id="otp-submit" class="button primary" type="submit">${otpRequested ? 'Xác nhận mật khẩu mới' : 'Gửi mã xác nhận'}</button>
        </form>
        <p class="card-footnote"><a data-auth-switch href="<c:url value='/auth/login'/>">Quay lại đăng nhập</a></p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>