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
- Thêm workflow CD chỉ nhận sự kiện `push` vào `main` (merge PR hoặc push trực
  tiếp), gọi lại CI trước khi deploy. CD chỉ chạy khi CI thành công; dùng
  self-hosted runner `crave-ec2`, label `crave-production` và environment `production`.
- Loại bỏ kích hoạt deploy thủ công `workflow_dispatch`. Các sự kiện
  `pull_request`, `pull_request_target`, `pull_request_review` không chạy CD.
  PR đang chờ review hoặc đã Approve nhưng chưa merge giữ nguyên website.
- Kiểm tra tên runner trước khi checkout; CD chỉ lấy code từ commit mới trên
  `main`, artifact WAR thuộc cùng lần CI thành công.
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
- `scripts/deploy-ec2.sh`, `tests/scripts/deploy-ec2-test.sh`.
- `docs/project/ci-cd-ec2.md`, README và các mục lục docs.

## Kiểm chứng ban đầu

Các kết quả sau thuộc lần triển khai CI/CD trước khi siết điều kiện chỉ deploy
từ `push` vào `main`:

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
- [CI thực tế trên GitHub](https://github.com/nguyenvinhz/Servlet_Project_Crave/actions/runs/38163598770)
  của [PR #29](https://github.com/nguyenvinhz/Servlet_Project_Crave/pull/29)
  đạt trên Ubuntu 24.04/JDK 17/Node.js 24/MySQL 8.4: 315 Maven tests, 11 Java
  assertion cases, 76 browser scenarios và 19 deploy/rollback scenarios. WAR
  và test reports đã upload thành công. Kiểm thử Linux dùng `flock` thật, nhưng
  không mô phỏng deploy cạnh tranh hoặc server EC2 thực tế.

## Kiểm chứng điều kiện chỉ deploy từ push main

- Actionlint 1.7.12 đạt với hai workflow đã chỉnh. CD chỉ có trigger `push`
  cho `main`; cả job CI và CD kiểm tra event/ref, CD thêm điều kiện
  `needs.ci.result == 'success'`.
- CI và CD checkout chính xác `github.sha`. Job production kiểm tra
  `RUNNER_NAME == crave-ec2` trước khi checkout, giữ environment `production`.
- Đã đọc cấu hình GitHub thực tế: runner `crave-ec2` online, có label
  `self-hosted`, `Linux`, `X64`, `crave-production`; environment `production`
  chỉ cho branch `main`, không có required reviewers hoặc wait timer.
- Giữ nguyên script deploy đã kiểm thử: backup, thay WAR, recreate riêng
  `app` với `--no-deps`, kiểm tra health và rollback. Không sửa script, cấu
  hình hoặc dữ liệu production trong lần điều chỉnh này.
- Kết quả CI cho cập nhật này xem tại [checks của PR #29](https://github.com/nguyenvinhz/Servlet_Project_Crave/pull/29/checks).

## Giới hạn và thiết lập còn lại

- Chưa deploy lên EC2 trong task này. Đã đối chiếu GitHub, runner
  `crave-ec2`, label `crave-production`, environment `production` và đường dẫn
  `/home/ubuntu/crave` đã có. Đưa workflow đã chỉnh vào `main` sẽ kích hoạt
  CI → CD tự động; [hướng dẫn](../../project/ci-cd-ec2.md) mô tả luồng và cách
  đối chiếu thiết lập một lần.
- Recreate Tomcat gây gián đoạn ngắn và mất session đăng nhập. Rollback khắc
  phục lỗi recreate/readiness; EC2 crash hoặc force kill cần xử lý trên server.
- CD không tự chạy migration database. Health endpoint thể hiện trạng thái
  kết nối database tại thời điểm khởi động ứng dụng.
