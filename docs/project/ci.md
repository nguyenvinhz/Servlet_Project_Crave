# Kiểm thử CI của Crave

CI chạy khi tạo/cập nhật PR vào `main`, khi workflow deploy gọi lại CI hoặc khi
chọn Run workflow để kiểm tra thủ công. Chạy CI thủ công không kích hoạt deploy.
Một commit mới trên cùng PR hủy lượt CI cũ; lượt deploy trên `main` vẫn chạy tuần tự.

## Các nhóm kiểm tra

| Job | Nội dung |
|---|---|
| `checks` | Actionlint, ShellCheck, cú pháp JavaScript, test deploy/rollback, 76 kịch bản browser dùng API giả lập |
| `backend` | Thử migration từ schema cũ; dựng schema/seed mới; chạy JUnit bằng `crave_app`; build một WAR |
| `runtime-smoke` | Nhận WAR của backend; chạy trên Tomcat 10.1/JDK 17 với MySQL 8.4 và Chrome thật |
| `quality-gate` | Check `CI required`: mọi nhóm trên phải thành công, kể cả khi job khác bị lỗi hoặc bỏ qua |

`checks` và `backend` chạy độc lập. `runtime-smoke` phụ thuộc `backend`; không
build lại WAR. CD nhận tên artifact từ `quality-gate` và chỉ deploy sau khi cả
workflow CI thành công.

Trong branch protection/ruleset của `main`, yêu cầu check **CI required**.
Nếu trước đây đã yêu cầu `Build and test`, cập nhật sang check mới sau khi PR
đã chạy và GitHub hiển thị tên check. Vẫn áp dụng quy tắc review của nhóm.

## Chạy kiểm tra cục bộ

Kiểm tra nhanh cần actionlint 1.7.12+, ShellCheck và Node 22+:

```bash
bash scripts/ci/checks.sh
bash scripts/tests/deploy-ec2-test.sh
node frontend/tests/account-browser-smoke.cjs
```

Test Java dùng JDK 17/21 và database đã được tạo từ schema/seed hiện tại:

```bash
mvn --batch-mode --no-transfer-progress --file backend/pom.xml clean verify
```

Đặt `DB_URL`, `DB_USER`, `DB_PASSWORD` như hướng dẫn trong README. Các test
cart/promotion đã dùng JUnit; không cần gọi `main()` hoặc `java -ea` riêng.
Surefire báo lỗi nếu không tìm thấy test.

## Database riêng cho CI

Root chỉ tạo schema/seed và cấp quyền. Test và Tomcat dùng `crave_app`, được cấp
`SELECT`, `INSERT`, `UPDATE`, `DELETE`, `EXECUTE` trên `crave.*`; không có quyền
tạo/xóa bảng, thay routine hoặc quản trị server. Các mật khẩu trong script CI
chỉ dành cho database tạm. Tài khoản/routine definer trong production không bị
các script này thay đổi.

Để chạy các script database bằng Bash với Docker, tạo container mới:

```bash
docker run -d --name crave-ci-mysql \
  -e MYSQL_ROOT_PASSWORD=ci-root-password mysql:8.4
# Chờ container nhận được kết nối MySQL rồi thực hiện các bước tiếp theo.
export MYSQL_CONTAINER_ID=crave-ci-mysql
export MYSQL_ROOT_PASSWORD=ci-root-password
export CI_DISPOSABLE_DATABASE=true
bash scripts/ci/test-migrations.sh
bash scripts/ci/prepare-database.sh
docker rm -f crave-ci-mysql
```

Các bước này xóa/tạo database `crave` trong container đã chỉ định. Không dùng
container hoặc server production. Script cũng hỗ trợ server tạm cục bộ khi
đặt rõ `MYSQL_HOST`, `MYSQL_PORT`, và tùy chọn `MYSQL_BIN`, `MYSQL_DUMP_BIN`.
Xem [fixture migration](../../database/tests/README.md) để bổ sung bản nâng cấp khác.

## Kiểm tra ứng dụng thật

Workflow chuẩn bị database mới và fixture `runtime-fixtures.sql`, chạy WAR đã
build trên image `tomcat:10.1-jdk17-temurin`, rồi gọi
`frontend/tests/runtime-smoke.cjs`. MySQL/Tomcat thuộc môi trường CI; không dùng EC2.
Chrome dùng HTTP và session thật, không thay `fetch` và không tự chuyển đổi JSP.

Các trường hợp gồm: health ứng dụng/database, menu JSP đọc dữ liệu, guest nhận
401, đăng ký/đăng nhập qua giao diện, cookie session HttpOnly, giỏ hàng với
topping, voucher, checkout và đọc đơn đã lưu, giỏ rỗng sau checkout, customer
nhận 403 khi gọi API admin, logout và bảo vệ lại JSP/API.
Voucher của fixture dùng thời gian tương đối để test không phụ thuộc hạn của seed.

Nếu đã chạy WAR trên Tomcat cục bộ với database tạm và fixture trên:

```bash
export SMOKE_BASE_URL=http://127.0.0.1:8080/crave
export CHROME_PATH=/path/to/chrome
node frontend/tests/runtime-smoke.cjs
```

Script tạo tài khoản/đơn thử nghiệm; dùng database tạm và URL loopback. Test
backend và runtime dùng database tách biệt để dữ liệu của hai nhóm không ảnh hưởng nhau.

## Đọc kết quả lỗi

- `backend-test-reports`: báo cáo Surefire của từng lớp JUnit.
- `runtime-smoke-reports`: kết quả các bước, log Chrome/Tomcat, ảnh và HTML khi
  browser test thất bại.
- Log MySQL được in khi job backend/runtime thất bại.
- Job `CI required` thất bại nếu bất kỳ nhóm bắt buộc nào không có kết quả success.

WAR được upload sau backend để job runtime tải về, nhưng việc artifact tồn tại
chưa có nghĩa nó đã qua mọi kiểm tra. Chỉ deploy sau khi `CI required` thành công.
