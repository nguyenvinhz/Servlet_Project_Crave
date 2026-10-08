# Báo cáo: Khắc phục lỗi Day 1 (Vòng 3 - Order & Payment)

- **Người thực hiện:** Nguyễn Đức Phát
- **Ngày:** 2026-10-08
- **Branch:** `feature/order-payment-day1`
- **Tác vụ:** Cập nhật và xử lý phản hồi từ Gate 1 (PR #8)

## 1. Mục đích
Tiếp tục xử lý các phản hồi từ Reviewer sau lần review ngày 07/10 nhằm hoàn thiện Module Order & Payment.

## 2. Chi tiết các lỗi đã khắc phục (Changelog)

### 2.1. Lỗi định tuyến URL cập nhật trạng thái đơn hàng
- **Vấn đề:** Tại trang `admin-orders.jsp`, biến mã đơn hàng trong JavaScript bị thất thoát khi gửi request PATCH do ảnh hưởng từ JSP Expression Language (EL) đánh giá phía server.
- **Khắc phục:** Thay thế cú pháp Template Literal chứa `${}` của JavaScript bằng phương pháp nối chuỗi (`+`) để đảm bảo mã đơn hàng được truyền tải chính xác đến REST endpoint.

### 2.2. Khung quản lý Thanh toán (Admin)
- **Vấn đề:** Giao diện admin hiện tại mới chỉ hỗ trợ cập nhật trạng thái đơn hàng, thiếu khung quản lý thanh toán theo yêu cầu nghiệp vụ Day 1.
- **Khắc phục:** 
  - Bổ sung UI cập nhật thông tin thanh toán (Payment) bên trong `admin-orders.jsp`.
  - Hiển thị đầy đủ: Mã thanh toán, Phương thức, Số tiền, Trạng thái thanh toán hiện tại.
  - Tích hợp Fetch API gọi đến endpoint `PATCH /api/payments/{paymentId}/status` bằng payload JSON.

### 2.3. Chuẩn hóa Định dạng Code
- **Vấn đề:** Cảnh báo "trailing whitespace" ở một số dòng do git kiểm tra.
- **Khắc phục:** Loại bỏ triệt để các khoảng trắng thừa ở cuối dòng tại các file `admin-orders.jsp`, `checkout.jsp`, và `OrderResponseSerializationTest.java`.

### 2.4. Đồng bộ DTO và OpenAPI (Contract Sync)
- **Vấn đề:** Có sự chênh lệch thuộc tính giữa cấu trúc đối tượng trả về (`OrderResponse`) và tài liệu thiết kế API YAML.
- **Khắc phục:** Bổ sung các trường `foodNameSnapshot` và `note` vào `OrderItemResponse` trong file `docs/api-order-payment.yaml` để đồng bộ 100% với codebase thực tế.

### 2.5. Hoàn thiện Logic Entity (JPA)
- **Vấn đề:** Các thực thể (Entity) liên kết 2 chiều cần các Helper Method để tự động cập nhật các reference qua lại trước khi lưu vào cơ sở dữ liệu.
- **Khắc phục:** 
  - Thêm phương thức `setPaymentHelper(Payment payment)` vào `CustomerOrder.java` để đảm bảo giữ liên kết `Order - Payment`.
  - Thêm phương thức `addOption(OrderDetailOption option)` vào `OrderDetail.java` để liên kết `OrderDetail - Option`.

## 3. Tình trạng và Đề xuất
- Toàn bộ các yêu cầu bắt buộc giải quyết cho Gate 1 và các góp ý bổ sung trong đợt review ngày 8/10 đều đã được hoàn thành.
- Sẵn sàng chuyển sang các mục tiêu tối ưu hóa thêm hoặc chờ review lần cuối.
