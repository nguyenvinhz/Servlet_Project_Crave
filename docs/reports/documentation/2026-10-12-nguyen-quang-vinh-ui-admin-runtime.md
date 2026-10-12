# Hoàn thiện giao diện, quản trị và chạy dự án

Ngày kiểm tra: 2026-10-12. Nhánh: `fix/admin-ui-project-review`.

## Phạm vi đã thực hiện

- Cập nhật từ `origin/main` tại commit `5a3411f`. Giữ và chỉnh các thành phần đã có; mẫu Framer dùng để tham khảo màu sắc, bố cục và hình ảnh.
- Việt hóa nội dung, sửa encoding JSP, thống nhất định dạng tiền/ngày, sửa responsive và trạng thái lỗi/tải lại cho menu, ưu đãi, tài khoản, giỏ hàng, checkout và đơn hàng.
- Tạo shell quản trị theo mẫu `food-admin-dashboard`: tổng quan, đơn hàng, món ăn, danh mục, khách hàng, nhân viên, khuyến mãi và báo cáo.
- Tổng quan và báo cáo lấy dữ liệu MySQL thật, lọc theo ngày và xuất CSV. Doanh thu chỉ tính đơn hoàn tất có thanh toán thành công; khoản hoàn tiền được loại ra.
- Quản lý tài khoản và nhân sự theo quyền ADMIN/HR_MANAGER; quản lý khuyến mãi theo ADMIN/PROMOTION_MANAGER. Chặn tự khóa, tự hạ quyền và thao tác làm mất quản trị viên đang hoạt động.
- Sửa validation checkout/tùy chọn món, luồng nhận tại quán, chuyển trạng thái thanh toán, lịch sử đơn và tạo giỏ hàng đồng thời.
- Đối chiếu class diagram: bổ sung quan hệ JPA giữa khách hàng/giỏ hàng, đơn/khuyến mãi/nhân viên, chi tiết/món và lịch sử/nhân viên. Giữ ID tương thích API và snapshot giá trong đơn.
- Tách kiểm thử trình duyệt ra `tests/frontend/`, kiểm tra HTTP ra `tests/runtime/`, preview ra `docs/previews/`; lưu diagram và mô tả kiến trúc trong `docs/architecture/`.
- Thêm launcher Tomcat và công cụ tạo nhân viên cục bộ trong profile Maven `dev`. Sửa chọn JDK trên PowerShell 5 và phát hiện cổng bị chiếm.

## Kết quả kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Maven clean verify, Java 21, MySQL riêng | 384 kiểm thử đạt, không bỏ qua |
| Giao diện tài khoản/giỏ/checkout/đơn trên Chrome | 86/86 đạt |
| Biểu mẫu quản trị khách hàng/nhân viên/khuyến mãi | 3/3 đạt |
| Luồng đơn/thanh toán, biểu đồ/báo cáo, Esc và mở lại hộp thoại | 4/4 đạt |
| HTTP thật trên WAR, Tomcat 10.1 và MySQL | 90 request đạt |
| Tám trang admin thật trên Chrome, desktop/mobile | Không có lỗi JavaScript hoặc tràn ngang |
| Deploy và rollback qua môi trường giả lập | 19 kịch bản đạt |
| PowerShell chạy ứng dụng/tạo admin | Chạy được với JDK 21; tên tiếng Việt lưu đúng trong MySQL |
| Chạy launcher trên cổng đang bị chiếm | Thoát lỗi, không thông báo sẵn sàng sai |
| Đóng gói WAR | Không chứa test, preview, công cụ dev hoặc Tomcat nhúng |

HTTP kiểm tra đăng ký/đăng nhập, hồ sơ/địa chỉ, món/tùy chọn, voucher, giỏ hàng,
nhận tại quán/giao tận nơi, xác nhận/hoàn tiền, khóa tài khoản, các vai trò nhân viên
và toàn bộ trang admin. Database kiểm tra được tạo riêng; không reset database `crave` hiện có.

CI được bổ sung kiểm tra trình duyệt admin và chạy WAR qua HTTP trước khi upload artifact.
Các đoạn Bash và cú pháp đã kiểm tra cục bộ; workflow GitHub chưa chạy vì thay đổi chưa được push.

## Chạy lại

Xem [README](../../../README.md) để cấu hình DB, chạy `scripts/dev/run.ps1`
và tạo tài khoản bằng `scripts/dev/create-admin.ps1`.

Thanh toán chuyển khoản/ví hiện dùng luồng xác nhận trạng thái của dự án;
chưa có tích hợp cổng thanh toán ngoài. Kiểm tra local không xác nhận cấu hình EC2
hoặc dịch vụ ngoài môi trường này.
