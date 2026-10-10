# Báo cáo: Sắp xếp thư mục tài liệu

- Người thực hiện: Nguyễn Quang Vinh.
- Ngày: 2026-10-10.
- Branch: `docs/reorganize-documentation`.
- Task: Kéo repository từ GitHub và tổ chức lại `docs` để dễ tìm tài liệu.
- Phạm vi báo cáo: Toàn bộ branch `docs/reorganize-documentation`.

## Mục tiêu

Tài liệu API, kế hoạch, quy tắc Git và báo cáo đang nằm lẫn nhau; báo cáo có cả thư mục theo ngày, thư mục thành viên và file ở gốc. Gom tài liệu theo mục đích, thêm mục lục và thống nhất vị trí báo cáo.

## Đã thực hiện

- Lấy phiên bản `main` mới nhất từ GitHub và tạo nhánh tài liệu riêng.
- Chuyển hai hợp đồng OpenAPI vào `docs/api`, kế hoạch và quy tắc Git vào `docs/project`.
- Chuyển báo cáo vào `docs/reports/account`, `cart-voucher`, `menu-management` và `order-payment`; tên file chứa ngày, họ tên và task.
- Thêm mục lục tài liệu và mục lục báo cáo, cập nhật README gốc và tham chiếu tới các file đã chuyển.
- Cập nhật quy ước vị trí/tên báo cáo cùng các ví dụ Git để các báo cáo tiếp theo theo cùng bố cục.
- Giữ nội dung, ngày và người thực hiện của báo cáo cũ, bao gồm chỉnh sửa cục bộ của báo cáo tài khoản Ngày 2; chỉ đổi các đường dẫn liên quan.

Backend, frontend, hợp đồng API và database giữ nguyên nội dung; thay đổi này chỉ tổ chức tài liệu.

## File hoặc khu vực đã thay đổi

- `README.md`, `docs/README.md`, `docs/reports/README.md`.
- Các tài liệu trong `docs/api`, `docs/project` và `docs/reports`.

## Cách kiểm tra

- Kiểm tra các liên kết Markdown tương đối và đường dẫn tới tài liệu có tồn tại, đúng chữ hoa/thường.
- So sánh từng tài liệu đã chuyển với bản trước khi di chuyển: báo cáo chỉ thay đổi đường dẫn; các file API và kế hoạch giữ nguyên nội dung.
- Xác nhận không còn tham chiếu tới đường dẫn cũ và `git diff --check` đạt.

## Kết quả kiểm tra

- 23 liên kết Markdown nội bộ trong 16 file Markdown đã kiểm tra: đích tồn tại và đúng chữ hoa/thường.
- 13 tài liệu gốc, ngoài quy tắc Git được cập nhật theo bố cục mới, giữ nguyên nội dung sau khi thay đường dẫn; chỉnh sửa cục bộ của báo cáo tài khoản Ngày 2 được bảo toàn.
- Không còn tham chiếu tới 14 đường dẫn tài liệu cũ; `git diff --check` đạt.

## Ảnh hưởng và lưu ý

- Các đường dẫn tài liệu cũ đổi vị trí; README và tham chiếu trong repository đã được cập nhật.
- Link trong mô tả PR hoặc tài liệu bên ngoài repository cần cập nhật theo vị trí mới khi cần dùng trên nhánh này.
