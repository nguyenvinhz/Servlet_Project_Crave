# Báo cáo: Tách chức năng giỏ hàng và khuyến mãi Ngày 2

- Người thực hiện mã nguồn gốc: Ung Văn Trí.
- Ngày cập nhật báo cáo: 2026-10-09.
- Branch: `feature/cart-voucher-split-day2`.
- Task: Ngày 2 — hoàn thiện cart/voucher theo `docs/phan-cong-nhiem-vu-3-ngay.md`.
- Phạm vi báo cáo: phần chức năng cart/voucher được tách từ mã nguồn đã gộp, tiếp nối nền Ngày 1.

## Mục tiêu

Sắp xếp lại phần xử lý giỏ hàng và khuyến mãi cùng giao diện AJAX theo ranh giới Ngày 2. Đây là việc tách mã nguồn đã có sau khi thực hiện; không ghi nhận một lần hoàn thành chức năng mới hoặc xác nhận Gate 2 của cả nhóm.

## Đã thực hiện

### Backend

- Đưa triển khai JPA vào `CartRepository`, `CartItemRepository`, `PromotionRepository` để đọc giỏ hàng/khuyến mãi và thêm, cập nhật số lượng, xóa món hoặc xóa giỏ.
- Xử lý tùy chọn khi thêm món, kiểm tra trạng thái món/option, cộng dồn món có cùng bộ tùy chọn và đọc giá từ dữ liệu server.
- `CartService` tính đơn giá, thành tiền dòng và subtotal; `PromotionService` kiểm tra mã/trạng thái/hạn dùng/giá trị tối thiểu và tính giảm giá phần trăm hoặc số tiền cố định có chặn trần.
- Giữ cơ chế gọi `sp_calculate_discount` và tính dự phòng bằng Java khi lời gọi procedure không sẵn sàng. Không đưa thay đổi tài khoản Ngày 2 của Nguyễn Quang Vinh vào nhánh này.

### Frontend

- Kết nối cart và promotion bằng fetch/AJAX: tải giỏ, đổi số lượng, xóa món/xóa giỏ, tải danh sách mã, áp hoặc bỏ voucher.
- Render tùy chọn món, tổng tiền, thông báo lỗi cùng trạng thái loading/rỗng/thử lại trên khung JSP Ngày 1.
- Phần handoff cart → checkout và kiểm thử theo thời điểm đơn hàng được dành cho Ngày 3.

### API

- Hoàn thiện GET/POST/PUT/DELETE cart và các route tương thích cập nhật/xóa/xóa giỏ qua POST.
- Hoàn thiện tra cứu/danh sách promotion và `POST /api/promotions/validate` với request/response chung.
- Giữ các kiểm thử API và validator cơ bản cho phương thức/route, JSON lỗi và dữ liệu giỏ hàng không hợp lệ.

### Database

- Dùng bảng cart/promotion, view `v_cart_item_total`, `v_cart_summary` và contract procedure trong schema hiện có.
- Không chạy migration, thay đổi quyền database hoặc ghi dữ liệu người dùng khi tách nhánh.

## File hoặc khu vực thay đổi

- Repository/service: `CartRepository.java`, `CartItemRepository.java`, `PromotionRepository.java`, `CartService.java`, `PromotionService.java`.
- API/validation: `CartApiServlet.java`, `PromotionApiServlet.java`, `CartValidator.java`, `PromotionValidator.java`; DTO/mapper liên quan nếu cần cho chức năng.
- Frontend: `frontend/assets/js/cart.js`, JSP cart/promotion và phần giao diện nối API.
- Test: `CartApiServletTest.java`, `CartValidatorTest.java`, phần kiểm thử tính tổng/giảm giá/JSON/mapper trong `CartPromotionServiceTest.java`.

## Cách kiểm tra và kết quả

- `mvn -B -f backend/pom.xml clean verify`: 45 test đạt, 0 failure/error/skipped; tạo WAR thành công, bao gồm kiểm tra mapping với MySQL thật.
- Chạy riêng `CartPromotionServiceTest` bằng Java với `-ea`: 10 ca đạt. Procedure bị từ chối quyền gọi trên database hiện tại nên các ca tính giảm giá dùng Java fallback; kết quả này không chứng minh procedure chạy thành công.
- Tomcat 10.1 tạm thời: 26 kiểm tra HTTP/JSP đạt, gồm trang cart/promotion, route có dấu slash, asset, JSON khi cart chưa có danh tính khách hàng, API promotion công khai và POST order còn trả `501`.
- Kiểm tra cú pháp JavaScript bằng `node --check` cho script cart: đạt.

Đây là kết quả chạy mới trên nhánh đã tách. Bộ test dạng `main` được chạy riêng để thực thi assertion; kết quả không xác nhận Gate 2 của cả nhóm.

## Giới hạn và công việc tiếp theo

- Cart API của nguồn gốc còn nhận `customerId` từ session, header hoặc query. Chưa thể coi việc chọn giỏ hàng qua header/query là kiểm tra quyền truy cập đầy đủ.
- Validation voucher còn tin `subtotal` dương trong request; chỉ lấy lại subtotal giỏ khi request thiếu hoặc không dương. Yêu cầu dùng tổng tiền đáng tin cậy phía server của Gate 2 chưa được chứng minh đầy đủ.
- Các kiểm thử tính giảm giá dạng `main` dùng repository thật; kết quả có thể đi qua Java fallback khi procedure hoặc quyền gọi procedure không sẵn sàng.
- Tích hợp session thật thuộc phần phối hợp với tài khoản Ngày 2; handoff checkout và dữ liệu biên thuộc Ngày 3. Báo cáo này không xác nhận Gate 2 hoặc luồng end-to-end đã hoàn thành.
