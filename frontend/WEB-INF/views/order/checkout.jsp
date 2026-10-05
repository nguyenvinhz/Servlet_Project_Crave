<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Thanh toán | Crave</title>
</head>
<body>
    <h1>Trang Thanh toán</h1>
    <p>Chức năng Checkout sẽ được phát triển trong Ngày 2.</p>
    <form action="/crave/api/orders" method="POST">
        <label>Người nhận:</label>
        <input type="text" name="receiverName" /><br/>
        <label>Điện thoại:</label>
        <input type="text" name="receiverPhone" /><br/>
        <button type="submit">Xác nhận đặt hàng (501)</button>
    </form>
</body>
</html>
