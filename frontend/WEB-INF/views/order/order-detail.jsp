<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Chi tiết đơn hàng | Crave</title>
    <link rel="stylesheet" href="/crave/assets/css/styles.css">
    <style>
        .detail-container { max-width: 800px; margin: 40px auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .order-header { border-bottom: 1px solid #eee; padding-bottom: 15px; margin-bottom: 15px; }
        .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px; }
        .item-list { border-top: 1px solid #eee; padding-top: 15px; }
        .item-row { display: flex; justify-content: space-between; margin-bottom: 10px; border-bottom: 1px dashed #eee; padding-bottom: 10px; }
        .item-details small { color: #777; display: block; }
        .totals { margin-top: 20px; text-align: right; border-top: 1px solid #eee; padding-top: 15px; }
        .btn-back { display: inline-block; margin-bottom: 20px; text-decoration: none; color: #3498db; }
    </style>
</head>
<body>
    <div class="detail-container">
        <a href="/crave/orders" class="btn-back">← Quay lại danh sách</a>
        <div id="orderContent">Đang tải...</div>
    </div>

    <script>
        document.addEventListener('DOMContentLoaded', async () => {
            const container = document.getElementById('orderContent');
            // Lấy orderId từ URL. Ví dụ /crave/orders/C12345
            const pathSegments = window.location.pathname.split('/');
            const orderId = pathSegments[pathSegments.length - 1];

            try {
                const res = await fetch('/crave/api/orders/' + orderId);
                const data = await res.json();

                if (res.ok && data.success) {
                    const o = data.data;
                    container.innerHTML = `
                        <div class="order-header">
                            <h2>Đơn hàng #${o.orderId}</h2>
                            <p>Trạng thái: <strong>${o.status}</strong></p>
                            <p>Ngày đặt: ${new Date(o.orderedAt).toLocaleString('vi-VN')}</p>
                        </div>
                        <div class="info-grid">
                            <div>
                                <h4>Thông tin giao hàng</h4>
                                <p>Tên: ${o.receiverName}</p>
                                <p>SĐT: ${o.receiverPhone}</p>
                                <p>Địa chỉ: ${o.deliveryAddress || 'Nhận tại quán'}</p>
                            </div>
                            <div>
                                <h4>Thanh toán</h4>
                                <p>Hình thức: ${o.fulfillmentType === 'DELIVERY' ? 'Giao tận nơi' : 'Lấy tại quán'}</p>
                                <p>Ghi chú: ${o.customerNote || 'Không có'}</p>
                            </div>
                        </div>
                        <div class="item-list">
                            <h4>Danh sách món</h4>
                            ${o.details ? o.details.map(item => `
                                <div class="item-row">
                                    <div class="item-details">
                                        <strong>${item.quantity}x ${item.foodNameSnapshot}</strong>
                                        <small>${item.note || ''}</small>
                                    </div>
                                    <div>${(item.unitPrice * item.quantity).toLocaleString('vi-VN')} đ</div>
                                </div>
                            `).join('') : '<p>Chưa có chi tiết món ăn</p>'}
                        </div>
                        <div class="totals">
                            <p>Tạm tính: ${(o.subtotal || 0).toLocaleString('vi-VN')} đ</p>
                            <p>Phí giao hàng: ${(o.deliveryFee || 0).toLocaleString('vi-VN')} đ</p>
                            <h3>Tổng cộng: ${(o.totalAmount || (o.subtotal + o.deliveryFee)).toLocaleString('vi-VN')} đ</h3>
                        </div>
                    `;
                } else {
                    container.innerHTML = `<p style="color:red">${data.message}</p>`;
                }
            } catch (err) {
                container.innerHTML = '<p style="color:red">Lỗi kết nối máy chủ.</p>';
            }
        });
    </script>
</body>
</html>
