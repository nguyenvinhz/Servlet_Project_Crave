# Báo cáo: Dựng nền Order & Payment Ngày 1

- Người thực hiện: Nguyễn Đức Phát
- Ngày: 2026-10-06
- Branch: feature/order-payment
- Task/Issue: Ngày 1 — Đơn hàng và thanh toán
- Phạm vi báo cáo: Toàn bộ branch `feature/order-payment`

## Mục tiêu

Dựng nền cho Order, Order Detail, Order Status History và Payment. 
Tạo khung giao diện checkout, danh sách/chi tiết đơn, và admin xử lý đơn.
Chốt contract API cho tạo đơn, xem đơn, cập nhật trạng thái đơn, và thanh toán.

## Đã thực hiện

### Backend

- Tạo các Entity: `CustomerOrder`, `OrderDetail`, `OrderDetailOption`, `OrderStatusHistory`, `Payment`.
- Tạo Repository và Service interface cho các entity trên.
- Tạo các Servlet xử lý API (trả về 501 cho Ngày 1).

### Frontend

- Tạo các trang JSP khung: `checkout.jsp`, `orders.jsp`, `order-detail.jsp`, `admin-orders.jsp`.
- Chuẩn bị form đặt hàng, khu vực hiển thị danh sách đơn.

### API

- Khởi tạo hợp đồng OpenAPI tại `docs/api-order-payment.yaml`.
- Chốt request/response tạo đơn, cập nhật trạng thái đơn và thanh toán.

### Database

- Không thay đổi (Dùng chung DB schema).

## File hoặc khu vực đã thay đổi

- `docs/api-order-payment.yaml`
- `docs/reports/2026-10-06/02-nguyen-duc-phat-order-payment.md`
- `backend/src/main/java/com/foodordering/entity/...`
- `backend/src/main/java/com/foodordering/repository/...`
- `backend/src/main/java/com/foodordering/service/...`
- `backend/src/main/java/com/foodordering/servlet/...`
- `frontend/WEB-INF/...`

## Cách kiểm tra

- Kiểm tra file OpenAPI YAML.
- Maven build `mvn -f backend/pom.xml clean verify` để đảm bảo code compile thành công.

## Kết quả kiểm tra

- Passed (Sẽ bổ sung chi tiết sau khi push code)

## Ảnh hưởng và lưu ý

- Cần tích hợp với module Cart (của Trí) để lấy dữ liệu subtotal / discount. Ranh giới transaction đặt hàng (sẽ gọi CartService hoặc query trực tiếp v_cart_summary) sẽ được thảo luận thêm.
- Cần sử dụng Auth filter của Vinh để lấy customerId từ Session.

## Công việc còn lại

- Hoàn thiện code baseline (Entities, Servlets).
- Nhờ Trí review API contract xem định dạng JSON có thống nhất chưa.
- Chờ Gate 1 được cả nhóm thông qua.
