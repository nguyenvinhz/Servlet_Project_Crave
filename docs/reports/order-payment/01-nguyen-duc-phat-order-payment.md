# Báo cáo: Logic Nghiệp Vụ & Giao Diện Order & Payment (Ngày 2)

- Người thực hiện: Nguyễn Đức Phát
- Ngày: 2026-10-07
- Branch: feature/order-payment
- Task/Issue: Ngày 2 — Logic Đơn hàng, Thanh toán và Giao diện UI

## Mục tiêu

Hoàn thiện logic nghiệp vụ (Service & Repository Impl) cho các tính năng của module Đơn hàng & Thanh toán.
Kết nối API với giao diện Frontend qua kịch bản Ajax Fetch JS thay vì trả mã 501.

## Đã thực hiện

### Backend & API
- Xây dựng `CustomerOrderRepositoryImpl` và `PaymentRepositoryImpl`.
- Xây dựng `CustomerOrderServiceImpl`:
  - Fetch dữ liệu giỏ hàng qua native query (`v_cart_summary` và `v_cart_item_total`) làm nền tảng tính toán thay vì phải đợi Entity Cart (nếu có).
  - Khởi tạo Data Đơn hàng (`CustomerOrder`), chi tiết món (`OrderDetail`), copy tuỳ chọn vào (`OrderDetailOption`).
  - Xoá giỏ hàng sau khi đặt thành công.
  - Quản lý Database Transaction qua `EntityTransaction` để bảo đảm tính toàn vẹn (ACID).
- Thay thế các API 501 trong `OrderServlet`, `AdminOrderServlet`, và `PaymentServlet` bằng lời gọi API thực tế.
- Bổ sung `UpdateOrderStatusRequest`, `UpdatePaymentStatusRequest` DTO.

### Frontend
- Xây dựng trang Checkout (`checkout.jsp`) với form nhập liệu linh hoạt (ẩn hiện địa chỉ theo loại giao/nhận) và kết nối Ajax tới `POST /api/orders`.
- Xây dựng trang Lịch sử đơn hàng (`orders.jsp`) fetch dữ liệu GET `/api/orders` hiển thị danh sách đơn.
- Xây dựng trang Chi tiết đơn hàng (`order-detail.jsp`) lấy thông tin món ăn và trạng thái đơn.
- Xây dựng trang Admin Orders (`admin-orders.jsp`) cho phép update trạng thái qua `PATCH /api/admin/orders`.

## Vùng ảnh hưởng & Cần phối hợp
- Luồng tạo đơn hàng đã hardcode `customerId = "C001"` và `employeeId = "E001"`. **Cần ráp với Filter Auth của Vinh ở Ngày 3** để lấy ID từ Session.

## Hướng dẫn Test
1. Compile & deploy project lên Tomcat (dùng `mvn clean package`).
2. Mở `/crave/checkout` nhập thông tin và Submit. Trả về thành công và load sang trang `/crave/orders`.
3. Kiểm tra MySQL Table `customer_order` và `payment` để đảm bảo dữ liệu ghi đúng.

## Kết quả
- Pass cơ bản các kịch bản Frontend và Logic.
- Đã sẵn sàng cho Phase 3 (Ráp Auth, CI/CD).
