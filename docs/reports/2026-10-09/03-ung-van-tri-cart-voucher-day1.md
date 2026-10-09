# Báo cáo: Tách nền giỏ hàng và khuyến mãi Ngày 1

- Người thực hiện mã nguồn gốc: Ung Văn Trí.
- Ngày cập nhật báo cáo: 2026-10-09.
- Branch: `feature/cart-voucher-split-day1`.
- Task: Ngày 1 — dựng nền và chốt hợp đồng theo `docs/phan-cong-nhiem-vu-3-ngay.md`.
- Phạm vi báo cáo: phần nền cart/voucher được tách từ nhánh đã chứa công việc của nhiều ngày.

## Mục tiêu

Tách lại mã nguồn đã có theo phạm vi Ngày 1: domain, contract backend/API và khung giao diện. Đây là việc sắp xếp lại thay đổi sau khi thực hiện, không phải xác nhận nhóm đã hoàn thành Gate 1 tại thời điểm ban đầu.

## Đã thực hiện

### Backend

- Giữ nền JPA cho `Cart`, `CartItem`, `CartItemOption`, `Promotion`; enum, DTO, mapper và lỗi cart/promotion theo cấu trúc database hiện có.
- Giữ contract repository/service cho đọc và cập nhật giỏ hàng, tính tạm tính và xác thực voucher; phần xử lý nghiệp vụ thực tế được dành cho Ngày 2.
- Giữ các helper và đăng ký persistence cần thiết để nền cart/promotion cùng các domain đã có biên dịch được.

### Frontend

- Giữ JSP cart/promotion, khu vực nhập voucher, CSS và khung trạng thái rỗng, lỗi, loading.
- Giữ page servlet và route chuyển tiếp JSP. Chưa kết nối thao tác thêm/sửa/xóa hoặc áp voucher với nghiệp vụ thật.

### API

- Giữ request/response cart/promotion và envelope JSON chung `success/data/error`.
- Đăng ký `/api/cart`, `/api/cart/*`, `/api/promotions`, `/api/promotions/*` cùng route trang `/cart`, `/promotions`.
- Các endpoint nghiệp vụ cart/promotion trả `501 NOT_IMPLEMENTED` theo ranh giới scaffold Ngày 1; hoạt động CRUD và tính giảm giá thuộc Ngày 2.

### Database

- Sử dụng schema/seed MySQL sẵn có; không thực hiện migration hoặc thay đổi dữ liệu trong quá trình tách nhánh.
- Giữ ánh xạ bảng, view và contract procedure liên quan để triển khai ở ngày sau.

## File hoặc khu vực thay đổi

- Backend: entity/DTO/enum/mapper cart/promotion; `CartRepository`, `CartItemRepository`, `PromotionRepository`, `CartService`, `PromotionService` ở mức contract; helper JSON và lỗi liên quan.
- Route: `CartApiServlet`, `PromotionApiServlet`, `CartPageServlet`, `PromotionPageServlet`; đăng ký JPA trong `META-INF/persistence.xml`.
- Frontend: `frontend/WEB-INF/views/cart/index.jsp`, `frontend/WEB-INF/views/promotions/index.jsp`, `frontend/assets/css/cart.css` và phần khung cart/promotion liên quan.
- Test: contract route, JSON và scaffold Ngày 1; chưa đưa kiểm thử CRUD/tính giảm giá thực tế vào phạm vi ngày này.
- Báo cáo backend/API gộp trước đây được thay bằng báo cáo theo từng ngày; nội dung gộp không được dùng để chứng minh Ngày 1 đã có nghiệp vụ Ngày 2.

## Cách kiểm tra và kết quả

- `mvn -B -f backend/pom.xml verify`: 37 test đạt, 0 failure/error/skipped; tạo WAR thành công, bao gồm kiểm tra mapping với MySQL thật.
- Tomcat 10.1 tạm thời: 33 kiểm tra HTTP/JSP đạt cho `/cart`, `/promotions`, bốn trạng thái giao diện, route có dấu slash, asset và sáu endpoint nghiệp vụ trả JSON `501`.
- `git diff --check`: đạt.

Đây là kết quả chạy mới trên nhánh đã tách, không dùng lại số test của báo cáo gộp cũ và không xác nhận Gate của cả nhóm.

## Giới hạn và công việc tiếp theo

- Ngày 1 chưa thực hiện CRUD giỏ hàng, xử lý tùy chọn món, tính tổng hoặc áp voucher thực tế.
- Ngày 2 nối repository/service/API với dữ liệu thật và AJAX theo demo thêm món → sửa số lượng/tùy chọn → áp voucher → tính lại tổng.
- Ngày 3 phụ trách chuyển tiếp cart → checkout và kiểm thử tích hợp; checkout/order của Nguyễn Đức Phát và tài khoản Ngày 2 của Nguyễn Quang Vinh là các phần việc riêng.
- Báo cáo này không xác nhận Gate 1, Gate 2 hoặc Gate 3 của cả nhóm.

## Tích hợp nhánh Ngày 1 vào main

Main đã có cart/voucher hoạt động từ lần tích hợp trước. Khi merge nhánh Ngày 1, giữ nguyên API, repository/service, validator, JSP/JavaScript và các test chức năng hiện có; không thay chúng bằng scaffold hoặc xóa phần Ngày 2/Ngày 3 đã có.

Sáu kiểm thử `501` của snapshot Ngày 1 được điều chỉnh cho main thành bốn kiểm tra JSON chung khi cart chưa xác thực và hai kiểm tra page servlet forward JSP. Kết quả 37 test/33 kiểm tra phía trên thuộc nhánh scaffold đã tách; kết quả sau khi tích hợp vào main được kiểm tra riêng:

- Maven: 57 test đạt, 0 failure/error/skipped; tạo WAR thành công, gồm kiểm tra mapping với MySQL thật.
- Tomcat tạm thời: 26 kiểm tra HTTP/JSP đạt.
- Bộ manual test gốc chạy bằng Java với `-ea`: 11 ca đạt. Tài khoản database hiện tại thiếu quyền `EXECUTE` cho procedure nên phần tính giảm giá dùng Java fallback như giới hạn đã biết; không coi đây là kiểm chứng procedure thành công.
- So sánh với main trước khi tích hợp: mã nguồn trong `backend/src/main`, `frontend` và `database` không thay đổi; phần tích hợp thêm báo cáo và kiểm thử contract phù hợp với main hiện có.

Các kết quả này không xác nhận Gate của cả nhóm.

## Đính chính source của Pull Request

PR #17 trước đây được merge từ `fix/cart-voucher-day1-merge`, chưa đúng nhánh nguồn yêu cầu. Lần tích hợp sửa này dùng trực tiếp `feature/cart-voucher-split-day1`, sau commit revert thông thường của lần tích hợp trước; PR #17 vẫn là lịch sử.

Mã runtime cart/voucher giữ nguyên trong quá trình này; nhánh tài khoản Ngày 2 không thay đổi. Nhánh đúng nguồn đưa lại hai file thực tế là `CartPromotionApiContractTest.java` và báo cáo này, không tạo một PR rỗng chỉ để đổi metadata.

Đã kiểm tra lại trên nhánh feature đúng nguồn: Maven đạt 57 test, 0 failure/error/skipped, tạo WAR thành công và kiểm tra mapping với MySQL thật; Tomcat đạt 26 kiểm tra HTTP/JSP. Mã runtime backend/frontend/database khớp lần tích hợp trước và sáu contract test được đưa lại nguyên nội dung đã xác minh. Kết quả này không xác nhận Gate của cả nhóm.
