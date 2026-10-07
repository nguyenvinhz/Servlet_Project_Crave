<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My Orders" scope="request"/>
<%@ include file="../components/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/assets/css/order.css'/>">

<main class="page order-page order-theme">
    <div class="tracking-header" style="margin-bottom: 2rem;">
        <h1>My Orders</h1>
        <p>Review your past cravings and track current orders.</p>
    </div>

    <div class="orders-list" id="ordersContainer">
        <!-- Placeholder cho UI dev -->
        <a href="<c:url value='/orders/CRV-48291'/>" class="order-card">
            <div class="order-card-info">
                <h3>Order #CRV-48291</h3>
                <p>1 Item · 15 Oct, 2026</p>
                <span class="order-status-badge badge-delivering">Delivering</span>
            </div>
            <div class="order-card-right">
                <div class="price">$12.82</div>
            </div>
        </a>

        <a href="<c:url value='/orders/CRV-48110'/>" class="order-card">
            <div class="order-card-info">
                <h3>Order #CRV-48110</h3>
                <p>2 Items · 10 Oct, 2026</p>
                <span class="order-status-badge badge-completed">Completed</span>
            </div>
            <div class="order-card-right">
                <div class="price">$25.40</div>
            </div>
        </a>
    </div>
</main>

<script>
    // Script mẫu cho Frontend lấy danh sách API (Chưa gọi thật vì Day 1 Backend chỉ có tạo đơn)
    /*
    fetch('${pageContext.request.contextPath}/api/orders')
        .then(res => res.json())
        .then(json => {
            if(json.success) {
                // render json.data
            }
        });
    */
</script>

<%@ include file="../components/footer.jspf" %>
