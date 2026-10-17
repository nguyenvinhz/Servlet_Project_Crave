<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>

<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel">
        <p class="eyebrow">Chào mừng trở lại</p>
        <h1>Món ngon đang chờ bạn.</h1>
        <p>Đăng nhập để tiếp tục đơn hàng, địa chỉ giao nhận và ưu đãi riêng của bạn.</p>
    </section>

    <section class="card auth-card" aria-labelledby="login-title">
        <div class="card-heading">
            <p class="eyebrow">Tài khoản Crave</p>
            <h2 id="login-title">Đăng nhập</h2>
        </div>

        <div id="form-error" class="form-error" role="alert" aria-live="polite" ${empty error ? 'hidden' : ''}>
            <c:out value="${error}"/><c:forEach var="fieldError" items="${fieldErrors}"><p class="field-hint"><c:out value="${fieldError.value}"/></p></c:forEach>
        </div>
        <div id="form-success" class="form-success" role="status" aria-live="polite" ${empty message ? 'hidden' : ''}><c:out value="${message}"/></div>

        <form id="loginForm" action="<c:url value='/auth/login/submit'/>" method="post">
            <input type="hidden" name="returnTo" value="<c:out value='${param.returnTo}'/>">
            <label for="email">Email</label>
            <input id="email" name="email" type="email" value="<c:out value='${param.email}'/>" autocomplete="email"
                   maxlength="100" placeholder="ban@example.com" required>

            <label for="password">Mật khẩu</label>
            <input id="password" name="password" type="password" autocomplete="current-password"
                   minlength="8" maxlength="72" placeholder="Nhập mật khẩu" required>

            <p class="auth-help"><a data-auth-switch href="<c:url value='/auth/forgot-password'/>">Quên mật khẩu?</a></p>

            <button class="button primary" type="submit">Đăng nhập</button>
        </form>

        <p class="card-footnote">Chưa có tài khoản?
            <a data-auth-switch href="<c:url value='/auth/register'/>">Đăng ký ngay</a>
        </p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>