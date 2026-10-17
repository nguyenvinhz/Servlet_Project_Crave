<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>

<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel">
        <p class="eyebrow">Bảo vệ tài khoản</p>
        <h1>Mật khẩu mới, tài khoản an tâm.</h1>
        <p>Xác nhận mật khẩu hiện tại và mã gửi đến email của bạn trước khi thay đổi.</p>
    </section>
    <section class="card auth-card" aria-labelledby="change-title">
        <div class="card-heading">
            <p class="eyebrow">Tài khoản Crave</p>
            <h2 id="change-title">Đổi mật khẩu</h2>
            <p id="password-account" class="field-hint" role="status"><c:out value="${sessionScope.currentUser.email}"/></p>
        </div>
        <div id="form-error" class="form-error" role="alert" aria-live="polite" ${empty error ? 'hidden' : ''}><c:out value="${error}"/><c:forEach var="fieldError" items="${fieldErrors}"><p class="field-hint"><c:out value="${fieldError.value}"/></p></c:forEach></div>
        <div id="form-success" class="form-success" role="status" aria-live="polite" ${empty message ? 'hidden' : ''}><c:out value="${message}"/></div>
        <button id="password-session-retry" class="button outline" type="button" hidden>Thử tải lại tài khoản</button>
        <form id="changePasswordForm" action="<c:url value='${otpRequested ? "/auth/password/change/submit" : "/auth/password/change/code"}'/>" method="post"
              data-code-url="<c:url value='/auth/password/change/code'/>">
            <input type="hidden" name="otpActive" value="${otpRequested ? 'true' : 'false'}">
            <fieldset id="password-change-fields">
                <legend class="visually-hidden">Xác nhận và đổi mật khẩu</legend>
                <label for="currentPassword">Mật khẩu hiện tại</label>
                <input id="currentPassword" name="currentPassword" type="password" autocomplete="current-password"
                       minlength="8" maxlength="72" ${otpRequested ? '' : 'required'}>
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
                        <button id="otp-resend" class="button outline" type="submit" formaction="<c:url value='/auth/password/change/code'/>" formnovalidate>Gửi lại mã</button>
                        <button id="otp-edit" class="button outline" type="button">Nhập lại mật khẩu hiện tại</button>
                    </div>
                </fieldset>
                <button id="otp-submit" class="button primary" type="submit">${otpRequested ? 'Xác nhận mật khẩu mới' : 'Gửi mã xác nhận'}</button>
            </fieldset>
        </form>
        <p class="card-footnote">Sau khi đổi mật khẩu, bạn sẽ đăng nhập lại bằng mật khẩu mới.</p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>