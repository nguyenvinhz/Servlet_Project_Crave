<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageStyle" value="account" />
<%@ include file="../components/header.jspf" %>
<main id="main-content" class="page auth-page account-ui">
    <section class="intro-panel">
        <p class="eyebrow">Chào mừng trở lại</p><h1>Món ngon đang chờ bạn.</h1>
        <p>Đăng nhập để tiếp tục đơn hàng, địa chỉ giao nhận và ưu đãi riêng của bạn.</p>
    </section>
    <section class="card auth-card" aria-labelledby="login-title">
        <div class="card-heading"><p class="eyebrow">Tài khoản Crave</p><h2 id="login-title">Đăng nhập</h2></div>
        <%@ include file="../components/account-messages.jspf" %>
        <form id="loginForm" action="<c:url value='/auth/login'/>" method="post">
            <input type="hidden" name="returnTo" value="<c:out value='${param.returnTo}'/>">
            <label for="email">Email</label>
            <input id="email" name="email" type="email" autocomplete="email" maxlength="100"
                   value="<c:out value='${param.email}'/>" placeholder="ban@example.com" required>
            <label for="password">Mật khẩu</label>
            <input id="password" name="password" type="password" autocomplete="current-password"
                   minlength="8" maxlength="72" placeholder="Nhập mật khẩu" required>
            <label class="checkbox-row"><input name="rememberMe" type="checkbox" value="true">Ghi nhớ đăng nhập</label>
            <c:url var="forgotUrl" value="/auth/forgot-password"><c:param name="returnTo" value="${param.returnTo}"/></c:url>
            <p class="auth-help"><a href="<c:out value='${forgotUrl}'/>">Quên mật khẩu?</a></p>
            <button class="button primary" type="submit">Đăng nhập</button>
        </form>
        <c:url var="registerUrl" value="/auth/register"><c:param name="returnTo" value="${param.returnTo}"/></c:url>
        <p class="card-footnote">Chưa có tài khoản? <a href="<c:out value='${registerUrl}'/>">Đăng ký ngay</a></p>
    </section>
</main>
<%@ include file="../components/footer.jspf" %>
