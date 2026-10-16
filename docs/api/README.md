# Hợp đồng JSON trước đây

Các file OpenAPI trong thư mục này lưu lại thiết kế API JSON trước khi ứng dụng chuyển sang Servlet/JSP và form POST. Chúng là tài liệu lịch sử, không phải hợp đồng đang chạy.

- [Tài khoản, hồ sơ và địa chỉ](api-auth-profile-address.yaml).
- [Đơn hàng và thanh toán](api-order-payment.yaml).

Route, quyền truy cập và luồng hiện hành nằm trong [kiến trúc Servlet/JSP](../architecture/servlet-jsp.md). Trình duyệt gửi form và nhận HTML; JSON chỉ còn trong tích hợp máy chủ với payOS/geocoder và webhook payOS.
