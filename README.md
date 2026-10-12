# Crave Food Ordering

Ứng dụng Java Servlet/JSP đóng gói dạng WAR, dùng Jakarta Servlet 6, JPA/Hibernate,
HikariCP và MySQL.

Tài liệu được chia thành API, kế hoạch/quy ước làm việc và báo cáo theo chức năng.
Xem [mục lục tài liệu](docs/README.md) để tìm đúng phần cần đọc.

## CI/CD trên AWS EC2

Pull request vào `main` chạy kiểm thử và build WAR. Push/merge `main` chạy CI rồi
tự deploy ứng dụng vào `/home/ubuntu/crave` trên EC2, kiểm tra health và khôi phục
WAR cũ nếu deploy lỗi. Cài runner EC2 một lần theo
[hướng dẫn CI/CD](docs/project/ci-cd-ec2.md) để bật deploy tự động.

## Chạy môi trường phát triển

Yêu cầu: JDK 17+, Maven 3.9+, Docker (nếu chưa có MySQL) và Tomcat 10.1+.

Xem [cấu trúc mã nguồn và sơ đồ lớp](docs/architecture/README.md).

1. Tạo cấu hình database cục bộ:

   ```powershell
   Copy-Item .env.example .env
   # Sửa hai mật khẩu mẫu trong .env trước khi chạy.
   docker compose up -d
   ```

2. Cấu hình ứng dụng bằng biến môi trường. Không đặt mật khẩu thật trong source:

   ```powershell
   $env:DB_URL = 'jdbc:mysql://localhost:3306/crave?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8'
   $env:DB_USER = 'crave_app'
   $env:DB_PASSWORD = '<giống CRAVE_DB_PASSWORD trong .env>'
   ```

   Các biến tùy chọn: `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`,
   `DB_CONNECTION_TIMEOUT_MS`, `DB_VALIDATION_TIMEOUT_MS`, `DB_FAIL_FAST`.

   Với database đã tồn tại, áp dụng bản sửa voucher `INACTIVE` (issue #23) từ
   thư mục gốc repository bằng tài khoản sở hữu procedure:

   ```powershell
   mysql --user=root --password --database=crave --execute="SOURCE database/migrations/2026-10-11-active-promotion-discount.sql"
   ```

   Script thay thế riêng `sp_calculate_discount`; đơn cũ giữ snapshot giảm giá
   khi chỉ đổi trạng thái. Nếu đã cấp quyền `EXECUTE` riêng cho procedure,
   cấp lại đúng các quyền đó sau khi chạy migration. Chạy migration trong lúc
   tạm dừng ghi đơn vì MySQL thay thế routine qua `DROP`/`CREATE`.

3. Build và kiểm thử:

   ```powershell
   mvn -f backend/pom.xml clean verify
   ```

4. Deploy `backend/target/crave.war` lên Tomcat. Sau khi deploy, kiểm tra:

   - `GET /crave/api/health`
   - `/crave/auth/login`
   - `/crave/auth/register`
   - `/crave/customer/profile`
   - `/crave/customer/addresses`

   Hoặc chạy Tomcat 10.1 tích hợp bằng một lệnh từ thư mục gốc:

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/dev/run.ps1
   # Cổng khác: thêm -Port 8081
   ```

   Trên Linux/macOS: `bash scripts/dev/run.sh 8080` (JDK 17/21 trên PATH).
   Mở `http://localhost:8080/crave/menu`; nhấn Ctrl+C để dừng.
   Profile `dev` chỉ dùng cho launcher, không thêm Tomcat vào WAR production.

## Quản trị

Mẫu giao diện khách hàng tham khảo [Crave trên Framer](https://thrilled-calendar-459980.framer.app/),
giao diện quản trị dựa trên mẫu `food-admin-dashboard` người dùng cung cấp.
Trang quản trị dùng API và database của ứng dụng Servlet/JSP.

Tài khoản seed chưa có mật khẩu hợp lệ. Tạo tài khoản quản trị bằng công cụ cục bộ
(mật khẩu được nhập ẩn, băm bằng PBKDF2; email/số điện thoại phải chưa tồn tại):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev/create-admin.ps1 -Email admin@example.com -FullName 'Quản trị Crave' -Phone 0901234567
```

Đăng nhập tại `/crave/auth/login`, mở `/crave/admin`. Có thể truyền `-Role`
với `MENU_MANAGER`, `ORDER_STAFF`, `PROMOTION_MANAGER` hoặc `HR_MANAGER` để tạo nhân viên.
Admin truy cập mọi chức năng; nhân viên chỉ thấy chức năng phù hợp vai trò.

Không chạy lại `database/schema.sql` trên database có dữ liệu cần giữ:
đây là script bootstrap có `DROP DATABASE`. Dùng `database/migrations/` cho database hiện có.

Phần tài khoản Ngày 2 đã hoàn thiện: đăng ký, đăng nhập/đăng xuất, session,
cập nhật hồ sơ và quản lý địa chỉ giao hàng. Hợp đồng nằm tại
[`docs/api/api-auth-profile-address.yaml`](docs/api/api-auth-profile-address.yaml).

## Demo tài khoản Ngày 2 — Nguyễn Quang Vinh

1. Mở `/crave/auth/register`, đăng ký bằng email và số điện thoại chưa tồn tại.
2. Đăng nhập tại `/crave/auth/login`; ứng dụng chuyển tới hồ sơ.
3. Sửa họ tên, email hoặc số điện thoại và lưu; tải lại trang để kiểm tra dữ liệu.
4. Mở `/crave/customer/addresses`, thêm, sửa, chọn mặc định và xóa địa chỉ.
   Địa chỉ đầu tiên tự động là mặc định; xóa địa chỉ mặc định sẽ chọn một địa chỉ còn lại.
5. Đăng xuất; truy cập lại trang khách hàng sẽ chuyển về đăng nhập, API trả `401`.

Mật khẩu tài khoản trong `database/seed.sql` là placeholder và không đăng nhập được.
Hãy đăng ký tài khoản mới để demo. Ứng dụng lưu mật khẩu PBKDF2 có salt riêng;
API không trả hash hoặc mật khẩu. Session dùng cookie HttpOnly, SameSite=Lax và
hết hạn sau 30 phút không hoạt động. API hỗ trợ `rememberMe` kéo dài thời gian
không hoạt động lên 7 ngày; cookie vẫn chỉ tồn tại trong phiên trình duyệt.

Nhân viên đang làm việc có thể đăng nhập và xem hồ sơ; cập nhật hồ sơ và sổ địa chỉ
dành cho khách hàng. Filter bảo vệ các route khách hàng và phân quyền route quản trị
theo chức vụ. API quản trị menu yêu cầu `ADMIN`/`MENU_MANAGER`; API quản trị đơn và
thanh toán yêu cầu `ADMIN`/`ORDER_STAFF`. Các servlet kiểm tra lại session và vai trò
trước khi gọi service; ID khách hàng/nhân viên lấy từ session.

Phần tài khoản Ngày 3 bổ sung kiểm thử tích hợp auth với cart/voucher/order/payment,
validation Unicode cho dữ liệu tài khoản/địa chỉ và giao diện responsive với CSS
riêng. Xem [báo cáo Ngày 3](docs/reports/account/2026-10-11-nguyen-quang-vinh-account-day3.md).
Giao diện xóa voucher còn lưu của phiên trước khi đăng nhập thành công hoặc khi
phát hiện session hết hạn; `returnTo` hợp lệ vẫn đưa người dùng về trang đang thực hiện.

Demo kiểm tra quyền: chưa đăng nhập gọi `/api/cart`, `/api/orders` hoặc
`/api/admin/orders` nhận `401`; khách hàng gọi API quản trị nhận `403`; nhân viên
quản lý menu gọi `/api/admin/orders` nhận `403`. Đổi chức vụ hoặc khóa tài khoản
trong database sẽ áp dụng ở request bảo vệ tiếp theo.

Nếu máy đang dùng Java 25/26, chọn JDK 17 hoặc 21 trước khi chạy Maven để tương thích
với phiên bản Mockito/Byte Buddy hiện tại:

```powershell
$env:JAVA_HOME = '<đường dẫn JDK 17 hoặc 21>'
mvn -f backend/pom.xml clean verify
```

Kiểm thử tích hợp cần MySQL với schema/seed hiện có. Các test Ngày 2 tạo dữ liệu
riêng và dọn dữ liệu của mình sau khi chạy.

Kiểm thử giao diện tài khoản, giỏ hàng và checkout bằng Node 22+ và Chrome/Edge (API giả lập):

```powershell
node tests/frontend/account-browser-smoke.cjs
node tests/frontend/admin-management-browser-smoke.cjs
node tests/frontend/admin-orders-dashboard-browser-smoke.cjs
```

Nếu browser chưa được tìm thấy tự động, đặt `CHROME_PATH` tới file thực thi Chrome/Edge.

Kiểm thử HTTP trên WAR/Tomcat/MySQL thật (chạy với database test riêng đã seed,
công cụ sẽ tạo dữ liệu thử cho tài khoản, món ăn, khuyến mãi và đơn hàng):

```powershell
$env:CRAVE_SMOKE_BASE_URL = 'http://localhost:8080/crave'
$env:CRAVE_SMOKE_ADMIN_EMAIL = '<email admin của database test>'
$env:CRAVE_SMOKE_ADMIN_PASSWORD = '<mật khẩu admin của database test>'
node tests/runtime/http-smoke.cjs
```

## Demo cart/voucher Ngày 3 — Ung Văn Trí

1. Đăng nhập, thêm món và tùy chọn vào giỏ, áp voucher còn hiệu lực.
2. Đổi số lượng để kiểm tra voucher/tổng tiền được tính lại; giảm dưới mức tối thiểu
   để kiểm tra thông báo và bỏ giảm giá không còn hợp lệ.
3. Chuyển sang checkout; voucher được kiểm tra lại, tổng tiền lấy từ kết quả API.
   Chọn giao tận nơi (15.000đ) hoặc nhận tại quán (0đ).
4. Tạo đơn; kiểm tra tổng tiền/thanh toán và giỏ đã được xóa. Gửi lại request trên
   giỏ đã checkout nhận `400 CART_EMPTY` và không tạo thêm đơn.
5. Khóa voucher hoặc option sau khi thêm giỏ; checkout trả lỗi rõ và giữ giỏ
   để khách sửa trước khi thử lại.

Xem [báo cáo cart/voucher Ngày 3](docs/reports/cart-voucher/2026-10-09-ung-van-tri-cart-voucher-day3.md)
để biết phạm vi và kết quả kiểm thử.
