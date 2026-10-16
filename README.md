# Crave Food Ordering

Ứng dụng đặt món dùng **Jakarta Servlet 6 + JSP/JSTL + JPA/Hibernate + HikariCP + MySQL**, đóng gói WAR và chạy trên Tomcat 10.1+.

Servlet đọc `request.getParameter()`, gọi nghiệp vụ, đặt request attribute rồi forward JSP. Các thao tác ghi dùng form POST và redirect sau khi thành công. Trình duyệt nhận HTML, gửi form thông thường; JavaScript chỉ hỗ trợ giao diện, bản đồ và kiểm tra đầu vào. JSON chỉ còn phục vụ tích hợp máy chủ với payOS, dịch vụ tra tọa độ và webhook payOS.

Xem [kiến trúc Servlet/JSP](docs/architecture/servlet-jsp.md), [sơ đồ lớp và cấu trúc mã](docs/architecture/README.md), [mục lục tài liệu](docs/README.md).

## Cấu trúc

```text
backend/
  pom.xml
  src/main/java/com/foodordering/
    servlet/             Servlet menu, giỏ, checkout, đơn hàng, thanh toán
      account/           Đăng nhập, hồ sơ, địa chỉ, xác minh email
      admin/             Quản trị, báo cáo và các form quản lý
    filter/              UTF-8, xác thực, vai trò và kiểm tra nguồn POST
    listener/            Vòng đời pool database và cấu hình session
    bean/                Form và dữ liệu hiển thị JSP
    model/               Entity JPA
    dao/                 Truy cập dữ liệu bằng JPA
    service/             Nghiệp vụ, transaction và tổng tiền
    validator/           Kiểm tra dữ liệu
    integration/         payOS, geocoder và SMTP
    config/              Cấu hình database, email và payOS
    security/, enums/, exception/, util/
  src/main/resources/    persistence.xml và logging
  src/test/java/         Kiểm thử Java
  src/dev/java/          Launcher Tomcat và công cụ tạo nhân viên
frontend/
  WEB-INF/views/         JSP và thành phần dùng chung
  WEB-INF/web.xml
  assets/                CSS, JavaScript, hình ảnh và Leaflet
database/                Schema, seed và migration
scripts/                 Chạy local và triển khai EC2
tests/                   Kiểm thử trình duyệt, HTTP và deploy
docs/                    Kiến trúc, hướng dẫn và báo cáo
```

Các gói cũ `api`, `dto`, `mapper`, `repository`, `entity` và `servlet.page` đã được thay thế. JSP nằm trong `WEB-INF` và được Servlet forward; không mở trực tiếp file JSP.

## Chạy local

Yêu cầu JDK 17 hoặc 21, Maven 3.9+, MySQL và Tomcat 10.1+. Có thể dùng Docker cho database.

1. Tạo cấu hình và khởi động MySQL:

   ```powershell
   Copy-Item .env.example .env
   # Sửa các mật khẩu mẫu trong .env.
   docker compose up -d
   ```

2. Đặt biến môi trường khi cần ghi đè cấu hình local:

   ```powershell
   $env:DB_URL = 'jdbc:mysql://localhost:3306/crave?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8'
   $env:DB_USER = 'crave_app'
   $env:DB_PASSWORD = '<mật khẩu database>'
   ```

   Pool HikariCP nhận `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS` và `DB_VALIDATION_TIMEOUT_MS`. `DB_FAIL_FAST=true` yêu cầu dừng khởi động nếu database chưa sẵn sàng.

3. Build và kiểm thử:

   ```powershell
   mvn -f backend/pom.xml clean verify
   ```

4. Chạy Tomcat tích hợp:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/dev/run.ps1 -Port 8081
   ```

   Linux/macOS: `bash scripts/dev/run.sh 8081`. Mở `http://localhost:8081/crave/menu`; Ctrl+C để dừng. Hoặc deploy `backend/target/crave.war` lên Tomcat riêng. Profile `dev` chỉ phục vụ launcher, không đưa Tomcat vào WAR production.

Kiểm tra `GET /crave/health` để xem trạng thái database. `/crave/api/health` là đường dẫn tương thích, cùng trả văn bản thuần.

Không chạy lại `database/schema.sql` trên database cần giữ dữ liệu vì script bootstrap có `DROP DATABASE`. Với database hiện có, dùng `database/migrations/`; các thay đổi lần tái cấu trúc Java này giữ nguyên tên bảng.

## Chức năng và đường dẫn

Các đường dẫn dưới đây tính từ context path `/crave`.

| Chức năng | Đường dẫn |
|---|---|
| Thực đơn, chi tiết món | `/menu`, `/menu/detail?id=...` |
| Đăng ký, đăng nhập, đăng xuất | `/auth/register`, `/auth/login`, POST `/auth/logout` |
| Quên/đổi mật khẩu qua email | `/auth/forgot-password`, `/auth/change-password` |
| Hồ sơ, địa chỉ | `/customer/profile`, `/customer/addresses` |
| Giỏ hàng, voucher, checkout | `/cart`, `/cart/promotion`, `/checkout` |
| Đơn của khách, chi tiết/hủy | `/orders`, `/orders/{id}`, POST `/orders/{id}/cancel` |
| Khuyến mãi | `/promotions` |
| Quản trị, báo cáo/CSV | `/admin`, `/admin/reports` |
| Món, danh mục và tùy chọn món | `/admin/menu`, `/admin/categories` |
| Khách hàng, nhân viên, khuyến mãi | `/admin/customers`, `/admin/employees`, `/admin/promotions` |
| Xử lý đơn và thanh toán | `/admin/orders`, POST `/admin/payments` |
| Tạo/đồng bộ thanh toán, ảnh QR | `/payments/payos/{orderId}`, `/payments/payos/{orderId}/qr` |

Filter đưa người chưa đăng nhập về form đăng nhập, kiểm tra lại tài khoản ở mỗi request bảo vệ và phân quyền theo vai trò. Khách chỉ thao tác giỏ/đơn của mình; nhân viên dùng các trang quản trị phù hợp. ID người thực hiện lấy từ session.

Tài khoản seed có mật khẩu placeholder. Tạo quản trị viên local bằng công cụ nhập mật khẩu ẩn:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/create-admin.ps1 -Email admin@example.com -FullName 'Quản trị Crave' -Phone 0901234567
```

Có thể truyền `-Role` với `MENU_MANAGER`, `ORDER_STAFF`, `PROMOTION_MANAGER` hoặc `HR_MANAGER`. Admin truy cập toàn bộ chức năng. Mật khẩu được băm PBKDF2; dữ liệu hiển thị không chứa mật khẩu/hash. Session dùng cookie HttpOnly, SameSite=Lax; chọn ghi nhớ đăng nhập kéo dài thời gian session theo cấu hình hiện có.

## Tích hợp và triển khai

- [payOS](docs/project/payos.md): tạo VietQR theo tổng tiền đã lưu, đồng bộ trạng thái và kiểm tra chữ ký webhook. `POST /api/payos/webhook` là giao tiếp payOS với máy chủ.
- [Xác thực email](docs/project/email-verification.md): gửi mã OTP qua SMTP cho quên/đổi mật khẩu.
- [Giao hàng và sửa giỏ](docs/architecture/delivery-and-cart-editing.md): vị trí cửa hàng, xác nhận tọa độ và phí giao hàng. Tổng tiền được máy chủ tính lại khi checkout.
- [CI/CD EC2](docs/project/ci-cd-ec2.md): build WAR, deploy, kiểm tra health và rollback.

Khóa payOS/SMTP và mật khẩu database đặt trong `.env` local hoặc môi trường server; không đưa vào Git.

## Kiểm thử ứng dụng thật

Các browser smoke dùng Node 22+ và Chrome/Edge, kết nối ứng dụng đang chạy qua `CRAVE_SMOKE_BASE_URL`; không thay Servlet bằng API giả. Dùng database test riêng vì kiểm thử HTTP tạo tài khoản, món, khuyến mãi và đơn thử.

Chạy `node tests/runtime/smtp-fixture.cjs` trong terminal riêng. Trước khi khởi
động Tomcat test, đặt `EMAIL_SMTP_HOST=127.0.0.1`, `EMAIL_SMTP_PORT=2525`,
`EMAIL_SMTP_STARTTLS=false`, username/password SMTP rỗng, `EMAIL_FROM` là địa chỉ
test và `EMAIL_OTP_SECRET` là chuỗi test dài ít nhất 32 ký tự. Inbox mặc định ở
`http://127.0.0.1:2526`; cấu hình này chỉ dùng cho kiểm thử local/CI.

```powershell
$env:CRAVE_SMOKE_BASE_URL = 'http://127.0.0.1:8081/crave'
$env:CRAVE_SMOKE_ADMIN_EMAIL = '<admin của database test>'
$env:CRAVE_SMOKE_ADMIN_PASSWORD = '<mật khẩu admin>'
node tests/runtime/http-smoke.cjs
node tests/frontend/account-browser-smoke.cjs
node tests/frontend/admin-management-browser-smoke.cjs
node tests/frontend/admin-orders-dashboard-browser-smoke.cjs
```

HTTP smoke ghi tài khoản/đơn thử vào `backend/target/runtime-customer.env`.
Nạp các biến trong file này để chạy tiếp kiểm thử tài khoản đã đăng nhập, OTP,
giỏ/checkout và payOS; file chứa thông tin test nên không đưa vào Git. Nếu trình
duyệt không được tìm thấy tự động, đặt `CHROME_PATH` tới Chrome/Edge. Kiểm thử
script deploy/rollback: `bash tests/scripts/deploy-ec2-test.sh`.

Các file OpenAPI trong [docs/api](docs/api/) và báo cáo theo ngày ghi lại thiết kế trước đây; các route JSON trình duyệt trong đó không còn là hợp đồng hiện hành. Dùng [tài liệu Servlet/JSP](docs/architecture/servlet-jsp.md) để xem luồng và route đang sử dụng.
