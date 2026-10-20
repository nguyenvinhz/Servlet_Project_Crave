# Hợp đồng form Servlet/JSP

Các đường dẫn dưới đây dùng Servlet Jakarta và JSP. Form POST gửi
`application/x-www-form-urlencoded`; Servlet đọc tham số bằng
`request.getParameter()` hoặc `getParameterValues()`. Dữ liệu hiển thị nằm
trong request/session attributes. Sau khi lưu, Servlet redirect về trang GET
để tải lại dữ liệu từ database.

## Trang và thao tác

| Chức năng | Trang GET | Form POST |
|---|---|---|
| Đăng nhập/đăng xuất | `/auth/login` | `/auth/login/submit`, `/auth/logout` |
| Đăng ký và OTP | `/auth/register` | `/auth/register/code`, `/auth/register/submit` |
| Quên mật khẩu | `/auth/forgot-password` | `/auth/password/reset/code`, `/auth/password/reset/submit` |
| Đổi mật khẩu | `/auth/change-password` | `/auth/password/change/code`, `/auth/password/change/submit` |
| Hồ sơ | `/customer/profile` | `/customer/profile/save` |
| Địa chỉ | `/customer/addresses`, `?edit=ID` | `/customer/addresses/save`, `/delete`, `/default` |
| Thực đơn | `/menu`, `/menu/detail?id=ID` | Thêm món bằng `/cart/action` |
| Giỏ hàng | `/cart`, `/cart?edit=ID` | `/cart/action` với `action=add/update/remove/clear` |
| Khuyến mãi | `/promotions` | `/promotions/apply`, `/promotions/remove` |
| Thanh toán đơn | `/checkout` | `/orders/create` |
| Đơn của khách | `/orders`, `/orders/ID` | `/orders/cancel` |
| Tổng quan quản trị | `/admin` | Không có |
| Món và tùy chọn | `/admin/menu`, `?edit=ID`, `?options=ID` | `/admin/menu/action` |
| Danh mục | `/admin/categories` | `/admin/categories/action` |
| Khách hàng/nhân viên | `/admin/customers`, `/admin/employees` | `/admin/customers/action`, `/admin/employees/action` |
| Khuyến mãi quản trị | `/admin/promotions` | `/admin/promotions/action` |
| Đơn quản trị | `/admin/orders`, `?detail=ID` | `/admin/orders/action`, `/admin/orders/payment` |
| Báo cáo | `/admin/reports` | Không có; bộ lọc dùng query string |
| Health | `/health` (alias `/api/health`) | Không có |

Các hậu tố viết ngắn trong cùng ô giữ nguyên tiền tố trước đó. Ví dụ thao tác
xóa địa chỉ đầy đủ là `/customer/addresses/delete`.

## Các trường chính

- Tài khoản: `fullName`, `email`, `phone`, `password`, `confirmPassword`,
  `rememberMe`, `verificationCode`, `currentPassword` tùy form.
  Mật khẩu không được render lại trong HTML khi chuyển bước OTP.
  Đăng ký lưu thông tin đã kiểm tra và mật khẩu đã băm trong session tối đa
  10 phút; bước xác thực/gửi lại chỉ nhận mã hoặc dùng bản đăng ký tạm.
  Gửi mã thành công redirect về GET `/auth/register` để tải lại trang an toàn.
- Địa chỉ: `addressId`, `addressLine`, `note`, `isDefault`.
- Giỏ hàng: `action`, `foodId` hoặc `cartItemId`, `quantity`, `note`,
  nhiều trường `optionIds`. `updateOptions=true` xác định thao tác thay tùy chọn;
  không có cờ này thì giữ các tùy chọn hiện tại.
- Voucher: `code`, `returnTo` (chỉ nhận các trang cho phép).
  `selectedPromotionId` và `selectedPromotionCode` nằm trong session.
  Server xác thực lại subtotal và điều kiện khi xem cart/checkout và khi đặt đơn.
- Đặt hàng: `fulfillmentType`, `receiverName`, `receiverPhone`,
  `deliveryAddress`, `deliveryLatitude`, `deliveryLongitude`,
  `paymentMethod`, `customerNote`.
- Cập nhật đơn: `orderId`, `status`, `note`.
  Cập nhật thanh toán: `orderId`, `paymentId`, `status`, `transactionRef`.

ID tài khoản thao tác lấy từ `HttpSession`, không lấy từ form. Giá, giảm giá và
phí giao hàng do server tính; form không quyết định tổng tiền. Filter làm mới
trạng thái tài khoản và phân quyền, kiểm tra nguồn gửi của yêu cầu thay đổi dữ liệu.
Các Servlet cũng kiểm tra lại loại tài khoản/vai trò trước khi gọi service.

## Bản đồ và payOS

Bản đồ cần cập nhật tại chỗ nên dùng form POST tới `/delivery/search`,
`/delivery/reverse`, `/delivery/quote`. Servlet forward `order/delivery.jsp`
để trả HTML; JavaScript đọc các phần tử HTML này. Backend gọi Photon bằng
GeoJSON theo API của dịch vụ. Leaflet nhận ảnh PNG OSM qua
`GET /map/tiles/{z}/{x}/{y}.png`; Servlet giữ nguyên ảnh, attribution và header
cache của nhà cung cấp. Không áp dụng giới hạn JSON nghiệp vụ cho dịch vụ ngoài.

payOS gọi qua server. Form `POST /payos/create` nhận `orderId`;
`GET /payos/status?orderId=ID` trả đoạn HTML JSP và
`GET /payos/qr?orderId=ID` trả PNG. Callback
`POST /api/payos/webhook` giữ JSON vì đây là giao thức của payOS.
Các API JSON cũ khác đã được loại bỏ.

SMTP, payOS, CI/CD và Map dùng JSON, SDK hoặc định dạng mà nhà cung cấp yêu cầu
khi cần; các tích hợp này vẫn được gọi từ ứng dụng Servlet, không cần Spring.
