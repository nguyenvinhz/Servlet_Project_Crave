<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="../components/header.jspf" %>
<main class="page auth-page">
    <section class="intro-panel">
        <p class="eyebrow">Thành viên mới</p>
        <h1>Tạo tài khoản trong một phút.</h1>
        <p>Lưu thông tin nhận món, theo dõi đơn hàng và dùng ưu đãi dễ dàng hơn.</p>
    </section>

    <section class="card auth-card" aria-labelledby="register-title">
        <div class="card-heading">
            <p class="eyebrow">Bắt đầu với Crave</p>
            <h2 id="register-title">Đăng ký</h2>
        </div>

        <div id="form-error" class="form-error" role="alert" aria-live="polite">
            <c:out value="${error}"/>
        </div>

        <form id="registerForm" action="<c:url value='/api/auth/register'/>" method="post" data-method="POST">
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

            <button class="button primary" type="submit">Tạo tài khoản</button>
        </form>

        <p class="card-footnote">Đã có tài khoản?
            <a href="<c:url value='/auth/login'/>">Đăng nhập</a>
        </p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
