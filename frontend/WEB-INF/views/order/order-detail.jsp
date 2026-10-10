<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Order Tracking" scope="request"/>
<%@ include file="../components/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/assets/css/order.css'/>">

<main class="page order-page order-theme" style="max-width: 800px;">
    <div class="tracking-header" style="text-align: center;">
        <h1>Your order is on its way</h1>
        <p id="orderSubtitle">Order #CRV-48291 · Basil & Brick · Estimated arrival 18-25 min</p>
    </div>

    <!-- Stepper Status -->
    <div class="stepper" id="orderStepper">
        <div class="step done">
            <div class="step-icon">✓</div>
            <div class="step-label">Order placed</div>
        </div>
        <div class="step active">
            <div class="step-icon">2</div>
            <div class="step-label">Preparing</div>
        </div>
        <div class="step">
            <div class="step-icon">3</div>
            <div class="step-label">Out for delivery</div>
        </div>
        <div class="step">
            <div class="step-icon">4</div>
            <div class="step-label">Delivered</div>
        </div>
    </div>

    <!-- Order Item Card -->
    <div class="tracking-item">
        <h3>Smoky Stack Burger</h3>
        <p>1 Item · Paid by Cash on Delivery · Total $12.82</p>
    </div>
</main>

<script>
    // Frontend JS placeholder: Fetch detail từ backend và update DOM tương ứng
    // Các trạng thái (PENDING, PREPARING, DELIVERING, COMPLETED) sẽ mapping thành active / done class của stepper
</script>

<%@ include file="../components/footer.jspf" %>
