<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Lịch sử đơn hàng | Crave</title>
    <link rel="stylesheet" href="<c:url value='/assets/css/styles.css' />">
    <link rel="stylesheet" href="<c:url value='/assets/css/order.css' />">
</head>
<body class="order-theme order-page" data-context-path="<c:out value='${pageContext.request.contextPath}' />">
    <%@ include file="../components/header.jspf" %>

    <main class="page-shell">
        <div class="container orders-container" style="max-width: 900px; margin: 40px auto; padding: 0 1rem;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; gap: 1rem;">
                <h1 style="font-family: Georgia, serif; margin: 0; font-size: clamp(1.8rem, 3.5vw, 2.4rem);">Lịch sử đơn hàng</h1>
                <a href="<c:url value='/menu' />" class="btn-primary" style="width: auto; margin: 0; padding: 0.65rem 1.25rem; text-decoration: none; display: inline-flex; align-items: center; gap: 0.4rem;">+ Đặt món mới</a>
            </div>
            <div id="orderList" class="orders-list">
                <div class="card" style="padding: 2.5rem; text-align: center; color: var(--text-muted);">
                    <p>Đang tải danh sách đơn hàng...</p>
                </div>
            </div>
        </div>
    </main>

    <%@ include file="../components/footer.jspf" %>

    <script src="<c:url value='/assets/js/orders.js' />"></script>
</body>
</html>
