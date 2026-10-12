# Báo cáo: Hoàn thiện kiểm thử CI của Crave

- Người thực hiện: Codex
- Ngày: 2026-10-12
- Branch: `chore/improve-ci`
- Task: Cải thiện CI, chuẩn hóa JUnit, kiểm tra quyền database, migration và WAR trên Tomcat.
- Phạm vi báo cáo: Toàn bộ branch `chore/improve-ci`.

## Mục tiêu

Kiểm tra được cấu hình workflow, shell, frontend, nghiệp vụ với tài khoản database
ứng dụng và luồng HTTP/JSP/session của WAR trước khi cho phép deploy.

## Đã thực hiện

### Backend

- Chuyển 11 trường hợp `CartPromotionServiceTest` từ `main()`/Java `assert` sang JUnit.
- Bật `failIfNoTests` trong Surefire; bỏ bước tạo classpath/chạy assertion riêng.
- Bổ sung JavaBean getter cho CategoryResponse, FoodSummaryResponse,
  FoodDetailResponse và FoodOptionResponse. Kiểm tra Tomcat mới phát hiện
  `/menu` trả HTTP 500 vì JSP EL không đọc được record accessor. Getter giữ nguyên
  record component và tên thuộc tính API, đồng thời cho JSP đọc được dữ liệu.

### Frontend

- Giữ nguyên bộ browser test dùng API giả lập.
- Thêm browser test chạy với WAR thật: menu/chi tiết món, đăng ký/đăng nhập,
  session HttpOnly, cart/topping, voucher/checkout, đọc đơn, phân quyền và logout.
- Lưu log, kết quả JSON, ảnh và HTML trang nếu kiểm tra runtime thất bại.

### API

- Không thêm hoặc đổi endpoint/request/response nghiệp vụ.
- Kiểm tra HTTP 200/201/400/401/403 qua servlet/filter và cookie session thật.

### Database

- Root chỉ chuẩn bị database. Backend/Tomcat chạy bằng `crave_app` với quyền
  SELECT, INSERT, UPDATE, DELETE, EXECUTE trên `crave.*`.
- Thử migration voucher INACTIVE từ schema cũ cố định; so sánh dump dữ liệu
  trước/sau, giữ snapshot đơn cũ và kiểm tra trigger tính giảm giá cho đơn mới.
- Thêm fixture runtime có voucher dùng thời gian tương đối.
- Schema/seed/migration production không được workflow tự áp dụng.

### CI/CD

- Tách job `checks`, `backend`, `runtime-smoke` và check tổng hợp `CI required`.
- Các check nhanh và backend chạy độc lập; runtime dùng chính WAR từ backend.
- Workflow reusable chỉ xuất tên WAR cho CD sau khi tất cả job bắt buộc thành công.
- Hủy lượt CI cũ của cùng PR; giữ deploy trên main tuần tự.
- Thêm actionlint 1.7.12, xác minh checksum khi tải, và ShellCheck.
- Sửa cấu trúc kiểm tra sudo trong script deploy để vượt ShellCheck; không đổi
  điều kiện thành công/thất bại hoặc cơ chế rollback.
- Cho phép chạy CI thủ công để kiểm tra, không kích hoạt deploy thủ công.

## File hoặc khu vực đã thay đổi

- `.github/workflows/ci.yml`, `.github/workflows/deploy.yml`.
- `backend/pom.xml`, test CartPromotionServiceTest và bốn DTO menu.
- `scripts/ci/`, `scripts/deploy-ec2.sh`.
- `database/tests/`, `frontend/tests/runtime-smoke.cjs`.
- README, tài liệu CI/CD, mục lục tài liệu và báo cáo.

## Cách kiểm tra

- `bash scripts/ci/checks.sh`: actionlint, ShellCheck và cú pháp JavaScript.
- `bash scripts/tests/deploy-ec2-test.sh`: các tình huống deploy/rollback.
- `node frontend/tests/account-browser-smoke.cjs`: browser với API giả lập.
- MySQL 8.4.7 tạm: chạy test-migrations.sh rồi prepare-database.sh.
- `mvn --batch-mode --no-transfer-progress --file backend/pom.xml clean verify`
  với Java 17, DB_USER=crave_app và database tạm.
- Deploy WAR lên Tomcat 10.1.60/JDK 17, thêm runtime-fixtures.sql và chạy
  `node frontend/tests/runtime-smoke.cjs` bằng Chrome thật, Node 24.

## Kết quả kiểm tra

- Actionlint 1.7.12, ShellCheck 0.9.0, cú pháp JavaScript: đạt.
- 19 kịch bản deploy/rollback: đạt.
- 76 kịch bản browser dùng API giả lập: đạt.
- Migration MySQL 8.4.7: đạt; dữ liệu trước/sau giống nhau, snapshot cũ giữ nguyên,
  voucher INACTIVE không giảm giá cho đơn mới.
- 326 JUnit test: đạt, 0 failure, 0 error, 0 skipped, build WAR thành công.
- 8 nhóm runtime qua Tomcat/Chrome thật: đạt.
- `git diff --check`: đạt.

Trong môi trường kiểm tra cục bộ, Mockito không tự gắn agent được vào JVM;
lượt chạy toàn bộ test đã nạp Byte Buddy agent 1.15.4 khi JVM khởi động bằng
`-DargLine=-javaagent:<đường-dẫn-byte-buddy-agent.jar>`. Không bỏ qua test.
Các bước MySQL/Tomcat được chạy trực tiếp với cùng phiên bản chính và cấu hình
ứng dụng; Docker orchestration và cả workflow vẫn cần lượt xác nhận trên GitHub Actions.

## Ảnh hưởng và lưu ý

- Cập nhật required status check của main từ tên cũ sang `CI required` nếu đã
  cấu hình branch protection/ruleset. Thiết lập này nằm trên GitHub, không trong YAML.
- Merge vào main tiếp tục kích hoạt CI/CD hiện có; nhánh này chưa được merge.
- CD vẫn sử dụng artifact đã build và kiểm tra; không build lại hoặc chạy SQL trên EC2.
- Fixture migration hiện kiểm tra bản sửa voucher INACTIVE; migration mới cần
  thêm baseline và assertion thích hợp.
- Các script database yêu cầu CI_DISPOSABLE_DATABASE=true và chỉ dành cho server tạm.

## Công việc còn lại

- Chạy workflow đầy đủ trên GitHub Actions sau khi push/mở PR.
- Reviewer kiểm tra diff và kết quả check `CI required` trước khi merge.
