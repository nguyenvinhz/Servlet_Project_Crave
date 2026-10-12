<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Theo dõi đơn hàng | Crave</title>
    <link rel="stylesheet" href="<c:url value='/assets/css/styles.css' />">
    <link rel="stylesheet" href="<c:url value='/assets/css/order.css' />">
</head>
<body class="order-theme order-page" data-context-path="<c:out value='${pageContext.request.contextPath}' />">
    <%@ include file="../components/header.jspf" %>

    <main class="page-shell">
        <div class="container detail-container" style="max-width: 900px; margin: 40px auto; padding: 0 1rem;">
            <a href="<c:url value='/orders' />" class="btn-back" style="display: inline-block; margin-bottom: 1.5rem; text-decoration: none; color: var(--accent); font-weight: 600;">← Quay lại danh sách đơn hàng</a>
            <div id="orderContent">
                <div class="card" style="padding: 2.5rem; text-align: center; color: var(--text-muted);">
                    <p>Đang tải thông tin chi tiết đơn hàng...</p>
                </div>
            </div>
        </div>
    </main>

    <%@ include file="../components/footer.jspf" %>

    <script src="<c:url value='/assets/js/order-detail.js' />"></script>
</body>
</html>
