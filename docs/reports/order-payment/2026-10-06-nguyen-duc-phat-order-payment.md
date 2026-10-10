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
- Tạo Repository interface: `CustomerOrderRepository`, `PaymentRepository`.
- Tạo Service interface: `CustomerOrderService`, `PaymentService`.
- Tạo DTO: `OrderRequest`, `OrderResponse`.
- Tạo các Servlet xử lý API trả về HTTP 501: `OrderServlet`, `AdminOrderServlet`, `PaymentServlet`.

### Frontend

- Tạo các trang JSP khung: `checkout.jsp`, `orders.jsp`, `order-detail.jsp`, `admin-orders.jsp`.
- Chuẩn bị form đặt hàng, khu vực hiển thị danh sách đơn.

### API

- Khởi tạo hợp đồng OpenAPI tại `docs/api/api-order-payment.yaml`.
- Chốt request/response tạo đơn, cập nhật trạng thái đơn và thanh toán.

### Database

- Không thay đổi (Dùng chung DB schema).

## File hoặc khu vực đã thay đổi

- `docs/api/api-order-payment.yaml`
- `docs/reports/order-payment/2026-10-06-nguyen-duc-phat-order-payment.md`
- `backend/src/main/java/com/foodordering/entity/...`
- `backend/src/main/java/com/foodordering/repository/...`
- `backend/src/main/java/com/foodordering/service/...`
- `backend/src/main/java/com/foodordering/dto/...`
- `backend/src/main/java/com/foodordering/servlet/...`
- `frontend/WEB-INF/views/order/...`
- `frontend/WEB-INF/views/admin/...`

## Cách kiểm tra

- Kiểm tra file OpenAPI YAML để xác nhận contract.
- Mã nguồn đã được chia thành các commit nhỏ để dễ review.
- CI/CD tự động chạy kiểm tra `mvn clean verify` khi mở Pull Request.

## Kết quả kiểm tra

- Code structure, JPA mapping annotations đã hoàn thiện.
- Các route 501 API đã có.
- File Report cập nhật thành công (Passed).

## Ảnh hưởng và lưu ý

- Cần tích hợp với module Cart (của Trí) để lấy dữ liệu subtotal / discount. Ranh giới transaction đặt hàng đã được chốt (bao bọc tại CustomerOrderService.createOrder).
- Cần sử dụng Auth filter của Vinh để lấy customerId từ Session.

## Công việc còn lại

- Mở Pull Request để kiểm tra CI/CD.
- Chờ Gate 1 được cả nhóm thông qua.
