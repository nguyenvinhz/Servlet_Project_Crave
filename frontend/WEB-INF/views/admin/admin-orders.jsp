<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Quản lý đơn hàng (Admin) | Crave</title>
    <link rel="stylesheet" href="<c:url value='/assets/css/styles.css' />">
    <link rel="stylesheet" href="<c:url value='/assets/css/order.css' />">
</head>
<body class="order-theme order-page" data-context-path="<c:out value='${pageContext.request.contextPath}' />">
    <%@ include file="../components/header.jspf" %>

    <main class="page-shell">
        <div class="container admin-container" style="max-width: 1100px; margin: 40px auto; padding: 0 1rem;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; flex-wrap: wrap; gap: 1rem;">
                <div>
                    <h1 style="font-family: Georgia, serif; margin: 0; font-size: clamp(1.8rem, 3.5vw, 2.4rem);">Quản lý đơn hàng</h1>
                    <p style="color: var(--text-muted); margin: 0.25rem 0 0; font-size: 0.95rem;">Dành cho nhân viên xử lý đơn hàng (Order Staff)</p>
                </div>
                <button type="button" id="refreshBtn" class="admin-action-btn" style="padding: 0.6rem 1.2rem; font-size: 0.9rem;">🔄 Tải lại danh sách</button>
            </div>

            <div id="adminAlert" class="alert" style="display: none; padding: 1rem; margin-bottom: 1.25rem; border-radius: 8px;"></div>

            <div class="card" style="overflow-x: auto; padding: 0; border-radius: 12px;">
                <table class="admin-table">
                    <thead>
                        <tr>
                            <th>Mã Đơn</th>
                            <th>Khách Hàng</th>
                            <th>Thời Gian</th>
                            <th>Tổng Tiền</th>
                            <th>Trạng Thái Đơn</th>
                            <th>Thanh Toán</th>
                            <th>Thao Tác</th>
                        </tr>
                    </thead>
                    <tbody id="adminOrderList">
                        <tr><td colspan="7" style="text-align: center; padding: 2.5rem; color: var(--text-muted);">Đang tải danh sách đơn hàng...</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    </main>

    <%@ include file="../components/footer.jspf" %>

    <script src="<c:url value='/assets/js/admin-orders.js' />"></script>
</body>
</html>
