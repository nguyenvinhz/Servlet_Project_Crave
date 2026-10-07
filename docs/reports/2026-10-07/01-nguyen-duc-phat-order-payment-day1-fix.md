# Báo cáo: Khắc phục lỗi Day 1 (Order & Payment)

- **Người thực hiện:** Nguyễn Đức Phát
- **Ngày:** 2026-10-07
- **Branch:** `feature/order-payment-day1`
- **Tác vụ:** Sửa lỗi và hoàn thiện Gate 1 theo Feedback PR #2

## 1. Mục đích
Báo cáo này liệt kê chi tiết các sửa đổi đối với phần nền tảng (Day 1) của module Order & Payment, nhằm giải quyết triệt để các vấn đề kỹ thuật bị Reject trong PR trước đó.

## 2. Chi tiết các lỗi đã khắc phục (Changelog)

### 2.1. Lỗi biên dịch (Compile Errors)
- **Vấn đề:** 11 lỗi biên dịch liên quan đến `JsonUtils` và `ApiResponse.error(...)`.
- **Khắc phục:**
  - Các Servlet (`OrderServlet`, `AdminOrderServlet`, `PaymentServlet`) đã được đổi sang kế thừa `BaseApiServlet`.
  - Loại bỏ các import rác không tồn tại.
  - Sử dụng hàm `notImplemented(resp, "...")` chuẩn của `BaseApiServlet` để trả về HTTP 501.

### 2.2. Đăng ký Entity với JPA
- **Vấn đề:** Các entity mới chưa được đưa vào `persistence.xml`.
- **Khắc phục:** Đã thêm 5 entity (`CustomerOrder`, `OrderDetail`, `OrderDetailOption`, `OrderStatusHistory`, `Payment`) vào `persistence-unit`, giải quyết triệt để nguy cơ lỗi `UnknownEntityException` trong các ngày tiếp theo.

### 2.3. DTO OrderResponse
- **Vấn đề:** `OrderResponse` chỉ có 1 getter/setter, thiếu các field để giao tiếp với Frontend.
- **Khắc phục:**
  - Sinh toàn bộ Getter/Setter cho các trường dữ liệu hiện có.
  - Bổ sung thêm trường `List<OrderItemResponse> items` (và class lồng lồng `OrderItemResponse`) để mapping chuẩn xác với hợp đồng OpenAPI.

### 2.4. Page Route cho JSP
- **Vấn đề:** Các file JSP nằm trong `WEB-INF` nhưng chưa có Servlet/Controller để truy xuất.
- **Khắc phục:** Tạo thêm 3 Page Servlet:
  - `CheckoutPageServlet.java` -> map tới `/checkout`
  - `OrdersPageServlet.java` -> map tới `/orders/*`
  - `AdminOrdersPageServlet.java` -> map tới `/admin/orders`

### 2.5. Hợp đồng API (OpenAPI YAML)
- **Vấn đề:** `docs/api-order-payment.yaml` dùng trường `message` trong wrapper response thay vì `error` theo chuẩn của project.
- **Khắc phục:** Đã sửa lại định dạng response trong YAML để map chính xác với cấu trúc `ApiError` của `ApiResponse`.

### 2.6. Khác
- Đã đính chính lại file Báo cáo Ngày 1 (`docs/reports/2026-10-06/01-nguyen-duc-phat-order-payment.md`) để xác nhận Ranh giới Transaction (Transaction boundary) được bao bọc trực tiếp trong Service Interface `CustomerOrderService`, không chờ dời sang Ngày 2 nữa.

## 3. Tình trạng và Đề xuất
- **Tình trạng:** Khối lượng công việc nền tảng (Entity, DTO, API Contract, View routing) hiện đã hoàn thiện 100%. Lịch sử Git hoàn toàn sạch (đã loại bỏ code nghiệp vụ của Day 2/Day 3 khỏi nhánh này).
- **Đề xuất:** Sẵn sàng để được Review lại Gate 1.
