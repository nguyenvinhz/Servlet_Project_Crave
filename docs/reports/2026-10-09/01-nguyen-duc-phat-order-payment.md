# Báo cáo tiến độ - Day 2: Order & Payment

**Người thực hiện:** Nguyễn Đức Phát
**Ngày báo cáo:** 2026-10-09
**Module:** Order & Payment

## 1. Các công việc đã thực hiện trong Day 2

### Backend
- **Đồng bộ nhánh (Resolve Conflicts):** Đã resolve conflict giữa nhánh `feature/order-payment-day2` và `main` (chứa các bản vá giao diện từ Day 1). Giữ lại logic xử lý phức tạp của Day 2 và gộp với HTML/CSS của Day 1.
- **Tích hợp Auth:** Thêm logic kiểm tra quyền truy cập `customerId` cho `OrderServlet` và `employeeId` cho `AdminOrderServlet`, `PaymentServlet` thông qua session. Để tránh việc các API này chặn toàn bộ request (báo lỗi 401 Unauthorized) trong thời gian chờ Team hoàn thiện và merge chức năng Đăng nhập, một fallback giả lập (`?mock_customer=C001` và `?mock_employee=E001`) đã được thêm tạm thời vào API để phục vụ cho việc test Frontend.
- **Kiểm tra quyền sở hữu đơn hàng (Ownership Check):** Ở endpoint `GET /api/orders/{orderId}`, hệ thống sẽ so sánh ID chủ đơn hàng và ID người đang đăng nhập. Nếu không khớp sẽ trả về lỗi `403 Forbidden` nhằm bảo mật dữ liệu khách hàng.
- **Validation dữ liệu đầu vào:** Ở luồng tạo đơn hàng mới, backend đã ràng buộc bắt buộc nhập tên, SĐT, và nếu chọn hình thức giao tận nơi thì bắt buộc phải có địa chỉ.
- **State Machine cho trạng thái đơn:** Chặn đứng việc Admin cập nhật trạng thái đơn hàng sai luồng logic (ví dụ không thể chuyển từ COMPLETED về lại PREPARING).
- **Hoàn thiện DTO Mapping:** Xử lý map toàn bộ dữ liệu từ Entity `CustomerOrder` sang `OrderResponse` và `OrderSummaryResponse`, bao gồm cả `items` (chi tiết món), `payment` (trạng thái thanh toán), và `history` (lịch sử cập nhật đơn).
- **Phát triển Admin API:**
  - Thêm phương thức `findAll()` trong `CustomerOrderRepository`.
  - Cài đặt `getAllOrdersForAdmin()` trong `CustomerOrderServiceImpl`.
  - Viết logic bắt method GET trong `AdminOrderServlet` để trả về danh sách đơn hàng cho dashboard quản lý và bổ sung tài liệu YAML cho endpoint này.

### Frontend
*(Lưu ý: Các endpoint ở Frontend hiện tại đều đang được gắn tạm query param `?mock_customer=C001` và `?mock_employee=E001` để vượt qua vòng kiểm tra Auth của Backend. Khi module Đăng nhập hoàn tất, Frontend cần xóa các tham số này để sử dụng Auth Session thật).*
- **Thanh toán (checkout.jsp):** Cập nhật endpoint gọi POST request có kèm mock id để tạo đơn hàng. Đặc biệt, để lấy dữ liệu tổng tiền hiển thị lên màn hình, mình đã phải viết một đoạn script **gọi API Giỏ hàng giả lập** (`fetch('/crave/api/cart?mock_customer=C001')`) do module Cart của team chưa xong. Tương lai cần thay thế bằng API thật. Mình cũng cập nhật logic chuyển hướng để truyền mã đơn hàng qua tham số URL (`?new=ID`) khi thanh toán xong.
- **Lịch sử đơn hàng (orders.jsp):** Đổ dữ liệu lịch sử mua hàng, format trạng thái và giá tiền chuẩn xác. (Có sử dụng mock id ở endpoint fetch).
- **Chi tiết đơn hàng (order-detail.jsp):** Fix lỗi map data (`details` -> `items`), map đúng trường ngày tháng, chi phí và thêm mock id vào endpoint fetch chi tiết.
- **Quản lý đơn hàng Admin (admin-orders.jsp):** Kết nối bảng dữ liệu với API GET `/api/admin/orders?mock_employee=E001`, tích hợp luồng cập nhật trạng thái đơn thông qua JS fetch PATCH request.

## 2. Kết quả đạt được
- Hệ thống đã liên kết hoàn chỉnh luồng từ Frontend -> API -> Service -> DB.
- Có thể thao tác đặt hàng, xem danh sách đơn hàng (của khách) và cập nhật đơn hàng (của Admin) thành công.
- Không phát sinh lỗi biên dịch hay runtime nghiêm trọng.

## 3. Khó khăn / Vấn đề tồn đọng
- Module Authentication vẫn chưa ghép nối xong nên việc cấp quyền đang phải dựa hoàn toàn vào các tham số phụ (mock params) được chèn cố định ở các file Frontend để test giao diện. Khi ghép Auth, chúng ta phải nhớ xóa toàn bộ `?mock_customer=C001` và `?mock_employee=E001` ở các file `.jsp` và test lại toàn bộ luồng.
- Chức năng hiển thị tổng tiền ở trang thanh toán đang dùng API giả lập để gọi sang module Giỏ hàng (Cart). Khi Trí hoàn thành API Giỏ Hàng, cần đối soát lại tên endpoint và object trả về để sửa lại ở `checkout.jsp`.

## 4. Kế hoạch tiếp theo (Day 3)
- Chuẩn bị code review và merge request.
- Test kỹ lưỡng toàn bộ luồng với dữ liệu thực khi Auth đã sẵn sàng.
- Refactor các phần hiển thị số liệu / tooltip.
