# Crave Food Ordering

Ứng dụng đặt món dùng **Java Servlet, JSP/JSTL, JPA/Hibernate, HikariCP và MySQL**,
đóng gói thành WAR để chạy trên Tomcat 10.1+. Không dùng Spring/Spring Boot.

Form gửi dữ liệu bằng `POST`; Servlet đọc `request.getParameter()`, gọi service/DAO,
rồi forward sang JSP hoặc redirect sau khi lưu. JSP nhận dữ liệu qua request/session.
Không có lớp mapper riêng và không dùng JSON cho chức năng thông thường.
SMTP, payOS, CI/CD và Map tuân theo giao thức, định dạng và thư viện của nhà
cung cấp; được dùng JSON hoặc SDK khi cần. Các tích hợp này vẫn chạy cùng
ứng dụng Servlet/JSP, không yêu cầu Spring/Spring Boot.

## Cấu trúc

```text
pom.xml               File cấu hình Maven WAR duy nhất (packaging: war)
src/main/
  java/vn/edu/crave/
    controller/       Controller: Servlet hiển thị JSP và xử lý form (account, menu, cart, order, payment, admin)
    model/
      entity/         Entity JPA (Customer, Cart, CustomerOrder, Payment, Food, ...)
      dao/            Các lớp DAO thao tác database (AccountDAO, CartDAO, OrderDAO, ...)
      service/        Nghiệp vụ và transaction (AccountService, CartService, OrderService, ...)
      form/           Bean dữ liệu form request đầu vào
      viewmodel/      Bean dữ liệu chuẩn bị cho JSP
      validator/      Kiểm tra hợp lệ dữ liệu
      enums/          Enum trạng thái, vai trò
    filter/           UTF-8, session, phân quyền và kiểm tra nguồn gửi form
    config/           JPA/HikariCP và cấu hình tích hợp
    security/         Tài khoản trong HttpSession, băm mật khẩu
    integration/      payOS, Photon GeoJSON và HTTP ảnh bản đồ
    exception/, util/
  resources/
    META-INF/persistence.xml
    simplelogger.properties
  webapp/             Toàn bộ nguồn webapp (web root của WAR)
    WEB-INF/views/    JSP và các thành phần giao diện dùng chung
    WEB-INF/web.xml
    assets/           CSS, JavaScript, font, ảnh và Leaflet
    index.jsp
src/test/java/        Kiểm thử đơn vị và tích hợp (vn.edu.crave.*)
src/dev/java/         Launcher Tomcat và công cụ tạo quản trị viên (profile dev)
database/             Schema, seed và migration
scripts/dev/          Chạy local, chọn JDK và tạo tài khoản quản trị
tests/                Kiểm thử trình duyệt, HTTP và deploy
docs/                 Kiến trúc, cấu hình và báo cáo
```

Maven đóng gói toàn bộ `src/main/webapp/` làm web root của file WAR `target/crave.war`. CSS, font, ảnh và kiểu giao diện hiện tại
được giữ nguyên; JS/JSP chuyển cách tải dữ liệu sang form và HTML từ Servlet.
Bản đồ giao hàng và kiểm tra trạng thái QR nhận các đoạn HTML do JSP render.

## Chạy local

Cần JDK 17 hoặc 21, Maven, MySQL 8+ và Tomcat 10.1+.

1. Chép `.env.example` thành `.env`, sửa thông tin database/SMTP/payOS phù hợp.
   Có thể dùng `docker compose up -d` nếu chưa có MySQL.
2. Với database mới dành riêng cho thử nghiệm, chạy `database/schema.sql` rồi
   `database/seed.sql`. Schema bootstrap có `DROP DATABASE`; database đang có dữ liệu
   dùng các migration trong `database/migrations/`.
3. Build và kiểm thử:

   ```powershell
   mvn -Pdev verify
   ```

4. Chạy Tomcat local:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/dev/run.ps1 -Port 8081
   ```

   Linux/macOS: `bash scripts/dev/run.sh 8081`.
   Hoặc deploy `target/crave.war` lên Tomcat của bạn.

   Có thể chạy WAR bằng Docker với `docker compose --profile production up -d`
   sau khi file WAR đã tồn tại. Compose mặc định tiếp tục chỉ chạy MySQL.

Mở `http://localhost:8081/crave/menu`.
`GET /crave/health` trả `text/plain` với `application=UP` và `database=UP`
khi database hoạt động; còn giữ alias `/api/health` cho cấu hình deploy cũ.
Dừng máy chủ local trước khi chạy `mvn clean`, vì dữ liệu Tomcat phát triển nằm
trong `target/`.

Các biến database: `DB_URL`, `DB_USER`, `DB_PASSWORD`,
`DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS`,
`DB_VALIDATION_TIMEOUT_MS`, `DB_FAIL_FAST`. HikariCP được JPA quản lý vòng đời;
không mở connection pool cho từng request.

## Tài khoản, đặt món và quản trị

Đăng ký tại `/auth/register`, nhận OTP qua SMTP rồi xác minh. Đăng nhập tại
`/auth/login`; hồ sơ và sổ địa chỉ nằm tại `/customer/profile` và
`/customer/addresses`. Mật khẩu băm PBKDF2; session dùng cookie HttpOnly,
SameSite=Lax và được đổi ID khi đăng nhập. Các thao tác kiểm tra lại tài khoản
và vai trò ở server.

Từ `/menu`, thêm món vào `/cart`, áp dụng voucher rồi chuyển `/checkout`.
Giá, giảm giá, phí giao hàng và quyền sở hữu đơn được xác định ở server.
Đơn và thanh toán được lưu trong cùng transaction; lỗi sẽ rollback.
Khách xem hoặc hủy đơn ở `/orders`.

Tạo quản trị viên bằng công cụ local; mật khẩu được nhập ẩn:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/create-admin.ps1 -Email admin@example.com -FullName 'Quản trị Crave' -Phone 0901234567
```

Mở `/admin`. Nhân viên được phân quyền theo chức vụ; tài khoản seed chưa có
mật khẩu hợp lệ. Không có mật khẩu thật trong source.

## Kiểm thử giao diện và HTTP

Khởi động WAR với database test riêng và SMTP fixture cục bộ trước khi chạy:

```powershell
node tests/runtime/smtp-fixture.cjs
# Terminal khác: chạy Tomcat với EMAIL_SMTP_HOST=127.0.0.1, EMAIL_SMTP_PORT=2525,
# EMAIL_SMTP_STARTTLS=false, EMAIL_FROM và EMAIL_OTP_SECRET dành cho test.
$env:CRAVE_SMOKE_BASE_URL = 'http://127.0.0.1:8081/crave'
$env:CRAVE_SMOKE_INBOX_URL = 'http://127.0.0.1:2526'
node tests/runtime/http-smoke.cjs
node tests/frontend/servlet-form-browser-smoke.cjs
```

Kiểm tra riêng tìm địa chỉ và ghim bản đồ (không cần database/SMTP):

```powershell
node tests/frontend/delivery-map-browser-smoke.cjs
```

Browser smoke dùng Chrome/Edge, form thật và dữ liệu tài khoản test ngẫu nhiên.
Có thể đặt `CRAVE_SMOKE_ADMIN_EMAIL`/`CRAVE_SMOKE_ADMIN_PASSWORD` để kiểm tra quản trị.
Không dùng tài khoản hoặc database sản xuất cho smoke test.

Xem [hợp đồng form Servlet](docs/architecture/servlet-forms.md),
[kiến trúc](docs/architecture/README.md), [giao hàng và giỏ hàng](docs/architecture/delivery-and-cart-editing.md),
[SMTP/OTP](docs/project/email-verification.md), [payOS](docs/project/payos.md)
và [CI/CD EC2](docs/project/ci-cd-ec2.md).
Push/merge `main` hoặc **Run workflow** trên `main` chạy CI rồi deploy cùng WAR
đã kiểm tra SHA-256 lên runner EC2; cần cấu hình environment `production` theo hướng dẫn.
Chỉ đưa source, test, migration và cấu hình mẫu lên Git. `.env`, `database/seed_live.sql`,
`.runtime/` và `backend/target/` chỉ dùng local, đã được `.gitignore` loại trừ.
Deploy không nạp lại `schema.sql` hoặc seed vào database AWS đang có dữ liệu.
Hợp đồng đang dùng được mô tả trong tài liệu form Servlet; các OpenAPI JSON và
trang preview cũ đã được dọn khỏi project.
