# Báo cáo: Dựng nền tài khoản Ngày 1

- Người thực hiện: Nguyễn Quang Vinh
- Ngày: 2026-10-04
- Branch: feature/account-foundation
- Task/Issue: Ngày 1 — tài khoản, xác thực, hồ sơ và địa chỉ
- Phạm vi báo cáo: Toàn bộ thay đổi của branch `feature/account-foundation`

## Mục tiêu

Dựng nền Maven/Tomcat và kết nối JPA qua connection pool; khai báo domain
User, Customer, Address; tạo khung giao diện và route API cho đăng nhập,
đăng ký, hồ sơ và địa chỉ.

## Đã thực hiện

### Backend

- Thiết lập Maven WAR cho Jakarta Servlet 6 và Tomcat 10.1+.
- Cấu hình Hibernate/JPA sử dụng HikariCP và thông tin kết nối từ biến môi trường.
- Thêm listener khởi tạo/đóng connection pool và endpoint kiểm tra sức khỏe database.
- Tạo entity User, Customer, Address theo `database/schema.sql`.
- Thêm servlet render bốn trang thuộc phạm vi tài khoản.

### Frontend

- Tạo khung JSP cho login, register, profile và address.
- Bổ sung đầy đủ trường form, vùng thông báo lỗi/thành công và empty state địa chỉ.
- Thêm CSS responsive cho các màn hình tài khoản.

### API

- Tạo route nền cho auth, profile, address và health.
- Các route nghiệp vụ chủ động trả HTTP 501 trong Ngày 1; chưa triển khai logic Ngày 2.
- Chốt request, response và mã HTTP trong OpenAPI 3.0.

### Database

- Không thay đổi schema hoặc seed hiện có.
- Thêm Docker Compose để khởi tạo MySQL từ `schema.sql` và `seed.sql`.
- Không đưa credential thật vào source; ứng dụng đọc `DB_URL`, `DB_USER`,
  `DB_PASSWORD` từ môi trường.

### Quy ước Git và báo cáo

- Branch được đặt theo chức năng, không chèn tên thành viên.
- Báo cáo nằm trực tiếp trong thư mục ngày và có tên người thực hiện trong tên file.
- Toàn bộ branch sử dụng một báo cáo duy nhất; các commit sau tiếp tục cập nhật file này.

## File hoặc khu vực đã thay đổi

- `backend/pom.xml`
- `backend/src/main/java/com/foodordering/api`
- `backend/src/main/java/com/foodordering/config`
- `backend/src/main/java/com/foodordering/dto`
- `backend/src/main/java/com/foodordering/entity`
- `backend/src/main/java/com/foodordering/enums`
- `backend/src/main/java/com/foodordering/filter`
- `backend/src/main/java/com/foodordering/servlet`
- `backend/src/main/java/com/foodordering/utils`
- `backend/src/main/resources`
- `backend/src/test/java/com/foodordering`
- `frontend/WEB-INF`
- `frontend/assets/css/styles.css`
- `docs/api-auth-profile-address.yaml`
- `docs/quy-tac-git-github.md`
- `docs/reports/2026-10-04/01-nguyen-quang-vinh-account-foundation.md`
- `.env.example`, `.gitignore`, `compose.yaml`, `README.md`

## Cách kiểm tra

- Chạy `mvn -f backend/pom.xml clean verify`.
- Kiểm tra route đăng ký bằng unit test reflection trên `@WebServlet`.
- Kiểm tra mapping entity và JSON envelope bằng unit test.
- Chạy `git diff --check`.

## Kết quả kiểm tra

- Maven clean build: Passed.
- Unit test: Passed, 6 test, 0 failure, 0 error, 0 skipped.
- Đóng gói `backend/target/crave.war`: Passed.
- Deploy Tomcat thủ công: Chưa kiểm tra.
- Kết nối MySQL thực tế: Chưa kiểm tra vì Docker daemon trên máy chưa chạy.
- Render JSP trên server: Chưa kiểm tra vì chưa có Tomcat đang chạy.

## Ảnh hưởng và lưu ý

- Breaking change: Không có vì đây là nền dự án mới.
- Migration cần chạy: `database/schema.sql`, sau đó `database/seed.sql`.
- Cấu hình cần bổ sung: `DB_URL`, `DB_USER`, `DB_PASSWORD` trên môi trường Tomcat.
- Giới hạn hiện tại: route nghiệp vụ trả 501; chưa có repository, service,
  authentication filter hoặc nghiệp vụ Ngày 2.
- `ApiResponse` và `ApiError` đang là đề xuất cho contract của vertical slice tài khoản;
  cần Ung Văn Trí review trước khi dùng làm convention chung toàn dự án.
- Header/footer và stylesheet cần Nguyễn Minh Huân review trước khi coi là layout chung.

## Công việc còn lại

- Chạy MySQL, deploy WAR lên Tomcat và smoke-test health/JSP trước khi xác nhận Gate 1.
- Nhờ nhóm review domain model và OpenAPI contract.
- Thống nhất JSON/error convention với Ung Văn Trí.
- Thống nhất component/layout dùng chung với Nguyễn Minh Huân.
- Không bắt đầu nhiệm vụ Ngày 2 trước khi cả nhóm xác nhận Gate 1.
