# Báo cáo dọn project và cập nhật CI/CD AWS

Ngày hoàn tất: **2026-10-17**.

## Dọn file/thư mục không dùng

Đã kiểm tra tham chiếu trong source, test, JSP, cấu hình và scripts trước khi xóa:

- 6 DTO không còn dùng sau khi chuyển sang form:
  `AccountStatusUpdateRequest`, `EmployeeRoleUpdateRequest`,
  `FoodOptionStatusUpdateRequest`, `FoodStatusUpdateRequest`,
  `UpdateOrderStatusRequest`, `UpdatePaymentStatusRequest`.
- 6 file tài liệu/mẫu cũ trong `docs/api/` và `docs/previews/`:
  hai OpenAPI JSON, hai trang HTML preview và hai README của chúng.
- 25 thư mục rỗng: 20 package Java main/test cũ; `docs/api`, `docs/previews`,
  `frontend/WEB-INF/tags`, `tests/deployment`, `tests/http`.
- 6 cache local không còn tiến trình sử dụng: `account-validation`,
  `admin-jsp-check`, `all-jsp-check`, `final-jsp-check`,
  `servlet-validation`, `validation-launcher`.
- 4 helper local chỉ phục vụ refactor/kiểm tra một lần.

Đã sửa mục lục và liên kết tài liệu bị ảnh hưởng. Giữ nguyên báo cáo lịch sử,
diagram người dùng cung cấp, database local, cấu hình bí mật, bản recovery
và ảnh kiểm thử. Không xóa JS/CSS/JSP/ảnh/vendor đang được giao diện sử dụng.
Không đổi thêm hình thức frontend.

## CI/CD

- `ci.yml` giữ backend, form/browser, syntax, HTTP và deploy fixture checks.
  Thêm chạy lặp ba migration production trên database CI tạm thời.
- WAR chỉ được upload sau khi toàn bộ kiểm tra đạt, kèm manifest SHA-256 và
  output hash cho job deploy.
- `deploy.yml` nhận push/merge `main` hoặc **Run workflow** trên `main`.
  PR/nhánh khác không chạy production. Các lần deploy được tuần tự hóa.
- CD tải WAR của cùng lần CI và so SHA-256 với output CI trước khi gọi script.
  Artifact thiếu/sai checksum bị chặn.
- Dùng `/crave/health`; chỉ thử alias `/api/health` khi rollback WAR cũ.
  Giữ `.env`, compose và volume MySQL hiện có trên EC2; chỉ recreate ứng dụng.
- `compose.yaml` có Tomcat 10.1/JDK 17 trong profile tùy chọn `production`.
  Local mặc định vẫn MySQL-only; MySQL bind loopback. Mount WAR yêu cầu file
  thật, không tự tạo thư mục nếu thiếu.
- Bỏ fallback IP AWS cũ. Production phải đặt `PAYOS_PUBLIC_BASE_URL` trong
  GitHub environment. Geocoder mặc định là Nominatim XML.
- Cập nhật `.env.example`, README và
  [hướng dẫn EC2](../../project/ci-cd-ec2.md) cho bootstrap mới, runner,
  variables/secrets, deploy thủ công và rollback.

## Kết quả kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Maven `-Pdev verify` sau cleanup | **465 test đạt**, 0 lỗi, 0 bỏ qua; WAR build thành công |
| Deploy/rollback fixtures | **76 tình huống đạt** |
| Đăng ký webhook payOS | **12 test đạt** |
| Actionlint 1.7.12 | Hai workflow đạt |
| Cú pháp Bash | Hai scripts và 13 khối lệnh workflow đạt |
| Checksum gate | 3 tình huống đạt: đúng hash, sai hash, thiếu hash |
| Docker Compose thực tế | Default MySQL-only, production app+MySQL; mount/port/literal dollar đạt |
| Markdown links/Git diff | Đã kiểm tra liên kết và khoảng trắng |

Agent kiểm tra actionlint được lấy từ release chính thức và xác minh SHA-256.
Các action đang pin vẫn giữ đúng commit release chính thức. Không đổi version
action khi không cần thiết.

Log backend: `backend/target/cleanup-cicd-verify.log`.
Công cụ/kết quả kiểm tra CI/CD local nằm trong `.runtime/ci-cd-validation/`.
Bước replay migration với MySQL 8.4 container mới sẽ chạy khi CI thực thi trên
GitHub; local đã kiểm tra cấu hình và syntax của bước này.

## Trạng thái triển khai thật

Các thay đổi hiện nằm trong workspace. **Chưa push, chưa chạy GitHub Actions,
chưa thay đổi AWS production**. Trong phiên này GitHub chưa được kết nối và
không có GitHub/AWS CLI được cấu hình để điều khiển triển khai từ máy local.

Sau khi đưa thay đổi lên `main`, workflow sẽ tự chạy CI/CD. Cần runner
`crave-ec2` online, environment `production`, 5 secrets tích hợp và variable
`PAYOS_PUBLIC_BASE_URL` theo hướng dẫn EC2. Cũng có thể chọn **Run workflow**
trên `main` để triển khai lại, vẫn qua đầy đủ CI.