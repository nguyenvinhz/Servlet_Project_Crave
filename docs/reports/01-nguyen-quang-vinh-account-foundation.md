# Báo cáo: Dựng nền tài khoản Ngày 1

- Người thực hiện: Nguyễn Quang Vinh
- Ngày: 2026-10-04 (Cập nhật sau review: 2026-10-07)
- Branch: `feature/account-foundation`
- Task/Issue: Ngày 1 — tài khoản, xác thực, hồ sơ và địa chỉ
- Phạm vi báo cáo: Toàn bộ thay đổi của branch `feature/account-foundation` và các bản vá hoàn thiện Gate 1

## 1. Mục tiêu

Thiết lập Maven WAR/Tomcat và kết nối JPA/Hibernate qua connection pool HikariCP; xây dựng nền tảng domain cho `User`, `Customer`, `Employee` và `Address`; dựng khung giao diện JSP cùng client script gửi dữ liệu JSON; chốt hợp đồng API OpenAPI 3.0 và tạo các route nền tảng cho đăng nhập, đăng ký, hồ sơ và địa chỉ.

Đạt tiêu chuẩn **Gate 1**: ứng dụng khởi động được, kết nối cơ sở dữ liệu thật thành công với schema validation hợp lệ, và các route thuộc phạm vi có thể được gọi.

## 2. Công việc đã thực hiện

### 2.1. Backend & JPA Persistence

- **Thiết lập dự án Maven WAR:** Cấu hình Servlet 6.0 (Jakarta EE 10), Hibernate ORM 6.6.11.Final, HikariCP, MySQL Connector/J 9.2.0, Jackson 2.18.2 và JUnit 5.
- **Cấu hình Connection Pool & Cơ sở dữ liệu:**
  - `DatabaseConfig` quản lý `EntityManagerFactory` và HikariCP pool.
  - Hỗ trợ nạp cấu hình linh hoạt từ biến môi trường (`DB_URL`, `DB_USER`, `DB_PASSWORD`), file `.env` cục bộ và System properties.
  - Cấu hình `hibernate.hbm2ddl.auto=validate` bảo đảm schema database khớp chính xác với entity domain.
  - `DatabaseContextListener` quản lý vòng đời ứng dụng, kiểm tra kết nối khi khởi động và ghi nhận trạng thái UP/DOWN.
- **Xử lý triệt để lỗi Schema Validation (Khắc phục theo Review):**
  - Chuyển đổi các cột `account_type` (`user_account`), `status` (`customer`), `role` và `status` (`employee`) từ kiểu native `ENUM` sang `VARCHAR` kèm `CHECK constraint` trong schema database.
  - Đồng bộ mapping `@Column(length = ...)` trong entity để Hibernate 6 schema validation vượt qua 100%.
- **Bổ sung Hierarchy Entity `Employee`:**
  - Tạo entity `Employee` kế thừa `User` với `@DiscriminatorValue("EMPLOYEE")` và `@PrimaryKeyJoinColumn(name = "employee_id")`.
  - Tạo các enum `EmployeeRole` và `EmployeeStatus`.
  - Đăng ký đầy đủ `User`, `Customer`, `Employee`, `Address` trong `persistence.xml`, giải quyết hoàn toàn lỗi `Unrecognized discriminator value: EMPLOYEE` khi truy vấn tài khoản nhân viên.
- **Xử lý HTTP Method trong Servlet:**
  - Cập nhật `ProfileApiServlet` nhận `PUT` (cho REST API/JSON) và hỗ trợ ủy quyền `POST` (cho form submit) để tránh phát sinh lỗi `405 Method Not Allowed`.

### 2.2. Frontend & Giao diện

- **Khung giao diện JSP:**
  - `/auth/login` (`login.jsp`): form đăng nhập, ghi nhớ mật khẩu, liên kết đăng ký.
  - `/auth/register` (`register.jsp`): form đăng ký thành viên với xác nhận mật khẩu.
  - `/customer/profile` (`profile.jsp`): form cập nhật họ tên, email, số điện thoại.
  - `/customer/addresses` (`addresses.jsp`): sổ địa chỉ, trạng thái rỗng (empty state) và form thêm địa chỉ mới.
- **Tiêu chuẩn biểu mẫu & Trải nghiệm:**
  - Đầy đủ các trường dữ liệu, validation HTML5, accessibility attributes (`role="alert"`, `aria-live="polite"`).
  - Vùng hiển thị lỗi (`#form-error`) và thành công (`#form-success`).
- **Tích hợp Client-side JavaScript (`account.js`):**
  - Tạo `frontend/assets/js/account.js` tích hợp vào `footer.jspf`.
  - Tự động chặn submit form mặc định, đóng gói dữ liệu thành `application/json`, gửi HTTP request đúng method (`PUT` cho profile, `POST` cho login/register/address) theo đúng OpenAPI contract.
  - Xử lý phản hồi JSON, hiển thị thông báo lỗi hoặc thông điệp trạng thái thân thiện lên UI.

### 2.3. API & Hợp đồng giao tiếp (OpenAPI)

- **Định dạng Envelope chuẩn:** Thống nhất định dạng JSON qua `ApiResponse<T>` và `ApiError`, bảo đảm tính nhất quán trên toàn bộ API.
- **Tài liệu OpenAPI 3.0 (`docs/api-auth-profile-address.yaml`):**
  - Định nghĩa chi tiết các endpoint: `/api/health`, `/api/auth/login`, `/api/auth/register`, `/api/profile`, `/api/addresses`.
  - Mô tả đầy đủ Request Body (JSON), Response Codes (200, 201, 400, 401, 405, 409, 501, 503).
- **Trạng thái Ngày 1:** Các API nghiệp vụ chủ động trả `501 NOT_IMPLEMENTED` có kèm mã lỗi và thông điệp rõ ràng theo đúng ranh giới Ngày 1; riêng `/api/health` trả `200` khi database sẵn sàng hoặc `503` khi database lỗi.

### 2.4. Cơ sở dữ liệu (Database)

- Đồng bộ và chuẩn hóa `database/schema.sql` với `CHECK constraint` thay cho MySQL `ENUM`.
- Khởi chạy thành công trọn vẹn `schema.sql` và `seed.sql` trên MySQL 8.0 local:
  - 16 bảng dữ liệu, views và triggers.
  - 23 tài khoản người dùng mẫu (15 `CUSTOMER` từ `KH01` đến `KH15`, 8 `EMPLOYEE` từ `NV01` đến `NV08`).
  - Dữ liệu địa chỉ giao hàng (`DC01`..`DC05`), danh mục (`DM01`..`DM06`), món ăn và đơn hàng mẫu.

---

## 3. Danh sách file thay đổi & bổ sung

| Khu vực | File | Mô tả thay đổi |
|---|---|---|
| **Build** | `backend/pom.xml` | Thêm dependency Mockito cho test, cấu hình WAR plugin |
| **Config** | `backend/src/main/java/com/foodordering/config/DatabaseConfig.java` | Hỗ trợ nạp `.env`, fallback mật khẩu dev local `12345` |
| **Config** | `backend/src/main/resources/META-INF/persistence.xml` | Đăng ký `Employee`, bật `hbm2ddl.auto=validate` |
| **Entity** | `backend/src/main/java/com/foodordering/entity/User.java` | Cập nhật độ dài `account_type` thành 20 |
| **Entity** | `backend/src/main/java/com/foodordering/entity/Customer.java` | Cập nhật độ dài cột `status` thành 20 |
| **Entity** | `backend/src/main/java/com/foodordering/entity/Employee.java` | **Mới**: Entity Employee kế thừa User (`@DiscriminatorValue("EMPLOYEE")`) |
| **Enum** | `backend/src/main/java/com/foodordering/enums/EmployeeRole.java` | **Mới**: Enum chức vụ nhân viên (ADMIN, ORDER_STAFF, ...) |
| **Enum** | `backend/src/main/java/com/foodordering/enums/EmployeeStatus.java` | **Mới**: Enum trạng thái nhân viên (WORKING, ON_LEAVE) |
| **API** | `backend/src/main/java/com/foodordering/api/ProfileApiServlet.java` | Hỗ trợ nhận cả `PUT` và `POST` (ủy quyền sang doPut) |
| **Database** | `database/schema.sql` | Chuyển `ENUM` sang `VARCHAR` kèm `CHECK constraint` cho user/customer/employee |
| **Frontend** | `frontend/assets/js/account.js` | **Mới**: Script chặn submit form, gửi JSON theo OpenAPI |
| **Frontend** | `frontend/WEB-INF/views/components/footer.jspf` | Nạp `account.js` |
| **Frontend** | `frontend/WEB-INF/views/auth/login.jsp` | Thêm form ID và `data-method="POST"` |
| **Frontend** | `frontend/WEB-INF/views/auth/register.jsp` | Thêm form ID và `data-method="POST"` |
| **Frontend** | `frontend/WEB-INF/views/customer/profile.jsp` | Thêm form ID và `data-method="PUT"` |
| **Frontend** | `frontend/WEB-INF/views/customer/addresses.jsp` | Thêm form ID và `data-method="POST"` |
| **Testing** | `backend/src/test/java/com/foodordering/entity/AccountPersistenceIntegrationTest.java` | **Mới**: Integration test kiểm tra EMF validation và query Customer/Employee/Address trên MySQL |
| **Testing** | `backend/src/test/java/com/foodordering/api/FormApiContractTest.java` | **Mới**: Unit test kiểm tra servlet method contract |
| **Testing** | `backend/src/test/java/com/foodordering/entity/EntityMappingTest.java` | Bổ sung assertion cho Employee mapping |

---

## 4. Cách kiểm tra

1. **Khởi chạy cơ sở dữ liệu:**
   - Đảm bảo MySQL 8.0 đang chạy tại `localhost:3306`.
   - Nạp schema và seed:
     ```powershell
     mysql -u root -p12345 --default-character-set=utf8mb4 -e "source database/schema.sql"
     mysql -u root -p12345 --default-character-set=utf8mb4 -e "source database/seed.sql"
     ```
2. **Chạy bộ kiểm thử tự động:**
   ```powershell
   mvn -f backend/pom.xml test
   ```
3. **Đóng gói WAR:**
   ```powershell
   mvn -f backend/pom.xml verify
   ```

---

## 5. Kết quả kiểm tra

- **Toàn bộ 17/17 tests chạy thành công (Passed 100%):**
  - `FormApiContractTest`: 6/6 passed (ProfileApiServlet tiếp nhận PUT và POST form, AuthApiServlet tiếp nhận POST login/register, AddressApiServlet tiếp nhận CRUD, HealthApiServlet kiểm tra status UP/DOWN, AccountPageServlet forward đúng 4 trang JSP, DatabaseContextListener kích hoạt kết nối).
  - `RouteContractTest`: 2/2 passed (kiểm tra toàn bộ route URL pattern).
  - `ApiResponseTest`: 2/2 passed (kiểm tra serialize envelope JSON thành công và lỗi).
  - `EntityMappingTest`: 2/2 passed (kiểm tra mapping bảng, kế thừa joined, discriminator `CUSTOMER` và `EMPLOYEE`).
  - `AccountPersistenceIntegrationTest`: 5/5 passed:
    - `EntityManagerFactory` khởi tạo thành công với `hibernate.hbm2ddl.auto=validate` trên database thật.
    - Tìm và đọc thực thể `Customer` (`KH01`) kèm danh sách `Address` thành công.
    - Tìm và đọc thực thể `Employee` (`NV01`) với discriminator `EMPLOYEE` thành công.
    - Polymorphic query `SELECT u FROM User u WHERE u.id IN ('KH01', 'NV01')` tải chính xác cả 2 subtype.
    - Đọc thực thể `Address` (`DC01`) và truy xuất thông tin `Customer` liên kết thành công.
- **Đóng gói sản phẩm:** Tạo thành công `backend/target/crave.war`.
- **Độ ổn định:** Đã chạy lặp lại lệnh kiểm thử nhiều lần, kết quả build và test đều xanh tuyệt đối.

---

## 6. Kết luận

- Đã khắc phục toàn diện tất cả các vấn đề nêu trong biên bản đánh giá Ngày 1.
- Nền tảng tài khoản (`User`, `Customer`, `Employee`, `Address`), cấu hình kết nối database, các trang giao diện JSP, script xử lý JSON và hệ thống route API đã hoàn tất đồng bộ và chạy ổn định.
- **ĐÁNH GIÁ: ĐẠT GATE 1**, sẵn sàng cho các nhiệm vụ Ngày 2 (nghiệp vụ authentication, session filter, CRUD hồ sơ và địa chỉ).
