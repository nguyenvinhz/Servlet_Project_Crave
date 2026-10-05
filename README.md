# Crave Food Ordering

Ứng dụng Java Servlet/JSP đóng gói dạng WAR, dùng Jakarta Servlet 6, JPA/Hibernate,
HikariCP và MySQL.

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

Các route nghiệp vụ API Ngày 1 đã tồn tại nhưng chủ động trả HTTP `501` cho tới
khi logic Ngày 2 được triển khai. Hợp đồng đầy đủ nằm tại
[`docs/api-auth-profile-address.yaml`](docs/api-auth-profile-address.yaml).
