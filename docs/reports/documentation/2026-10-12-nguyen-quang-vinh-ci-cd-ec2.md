# Báo cáo: CI/CD tự deploy main lên EC2

- Người thực hiện: Nguyễn Quang Vinh.
- Ngày: 2026-10-12.
- Branch: `chore/main-auto-deploy`.
- Task: Tự kiểm thử, build và deploy lại khi push/merge `main`.
- Phạm vi báo cáo: Toàn bộ branch `chore/main-auto-deploy`.

## Mục tiêu

Ứng dụng đã deploy trên EC2 bằng Docker Compose tại `/home/ubuntu/crave`.
Thêm CI/CD để pull request chạy kiểm thử, còn `main` tự deploy artifact đã kiểm
thử lên server mà giữ nguyên database và cấu hình production.

## Đã thực hiện

- Thêm CI dùng GitHub-hosted Ubuntu, Java 17, Node.js 24 và MySQL 8.4 riêng.
  Chạy Maven `clean verify`, các assertion cart/promotion, JavaScript syntax,
  browser smoke và kiểm thử script deploy; xuất artifact `crave-war`.
- Thêm workflow CD chỉ nhận push `main` và chạy thủ công trên `main`, gọi lại CI
  trước khi deploy. Dùng self-hosted runner EC2 có label `crave-production`.
- Pin các GitHub Actions bằng full commit SHA đã xác minh từ release chính thức.
- Thêm script thay WAR qua staging và rename, backup WAR cũ, recreate riêng
  service `app` vì WAR dùng file bind mount, chờ HTTP/JSON health readiness.
- Khi deploy lỗi, khôi phục WAR cũ và recreate ứng dụng; workflow vẫn báo lỗi.
  Có concurrency không hủy lần deploy đang chạy và khóa `flock` trên server.
- Không áp dụng schema/seed, sửa `.env`, ghi đè compose hoặc recreate MySQL
  trong CD. Schema/seed chỉ chạy trên MySQL riêng của CI.
- Thêm hướng dẫn cài runner thành service, environment `production`, cách bật
  workflow và xử lý lỗi; cập nhật README và mục lục tài liệu/báo cáo.

## File hoặc khu vực đã thay đổi

- `.github/workflows/ci.yml`, `.github/workflows/deploy.yml`,
  `.github/actionlint.yaml`, `.gitattributes`.
- `scripts/deploy-ec2.sh`, `scripts/tests/deploy-ec2-test.sh`.
- `docs/project/ci-cd-ec2.md`, README và các mục lục docs.

## Kiểm chứng

- Actionlint 1.7.12: cả hai workflow hợp lệ, bao gồm context của dynamic MySQL
  port và label `crave-production`.
- Lệnh Maven dependency classpath dùng trong CI đã chạy được; Java assertions
  `CartPromotionServiceTest`: 11 trường hợp đạt.
- Bash syntax: script deploy và script kiểm thử đều đạt.
- Kiểm thử deploy/rollback: 19 kịch bản đạt, gồm health HTTP/JSON, lỗi recreate,
  lỗi staging/backup, TERM và các kiểm tra trước khi thay WAR. SHA xác nhận
  đúng WAR tại mỗi lần recreate; `.env`, compose và dữ liệu fixture giữ nguyên.
  Chạy bằng Git Bash/Python thật, Docker/curl giả lập. Git Bash dùng shim `flock`
  nên kết quả này không xác minh khóa trên Linux hoặc deploy EC2 thực tế.
- 36 liên kết Markdown nội bộ tồn tại; `git diff --check` đạt.

## Giới hạn và thiết lập còn lại

- Chưa chạy workflow thực tế trên GitHub hoặc deploy lên EC2 trong task này.
  Cần chủ repository cài runner trên EC2 và cấu hình environment `production`
  theo [hướng dẫn](../../project/ci-cd-ec2.md), rồi đưa workflow vào `main`.
- Recreate Tomcat gây gián đoạn ngắn và mất session đăng nhập. Rollback khắc
  phục lỗi recreate/readiness; EC2 crash hoặc force kill cần xử lý trên server.
- CD không tự chạy migration database. Health endpoint thể hiện trạng thái
  kết nối database tại thời điểm khởi động ứng dụng.
