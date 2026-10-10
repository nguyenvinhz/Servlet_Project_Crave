# Crave Food Ordering

Ứng dụng Java Servlet/JSP đóng gói dạng WAR, dùng Jakarta Servlet 6, JPA/Hibernate,
HikariCP và MySQL.

Tài liệu được chia thành API, kế hoạch/quy ước làm việc và báo cáo theo chức năng.
Xem [mục lục tài liệu](docs/README.md) để tìm đúng phần cần đọc.

## Chạy môi trường phát triển

Yêu cầu: JDK 17+, Maven 3.9+, Docker (nếu chưa có MySQL) và Tomcat 10.1+.

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
theo chức vụ. Các nghiệp vụ đơn hàng/thanh toán và quản trị menu vẫn thuộc phạm vi
thành viên phụ trách và hiện giữ scaffold Ngày 1 (`501`).

Nếu máy đang dùng Java 25/26, chọn JDK 17 hoặc 21 trước khi chạy Maven để tương thích
với phiên bản Mockito/Byte Buddy hiện tại:

```powershell
$env:JAVA_HOME = '<đường dẫn JDK 17 hoặc 21>'
mvn -f backend/pom.xml clean verify
```

Kiểm thử tích hợp cần MySQL với schema/seed hiện có. Các test Ngày 2 tạo dữ liệu
riêng và dọn dữ liệu của mình sau khi chạy.

Kiểm thử giao diện tài khoản bằng Node và Chrome/Edge (API giả lập):

```powershell
node frontend/tests/account-browser-smoke.cjs
```

Nếu browser chưa được tìm thấy tự động, đặt `CHROME_PATH` tới file thực thi Chrome/Edge.
