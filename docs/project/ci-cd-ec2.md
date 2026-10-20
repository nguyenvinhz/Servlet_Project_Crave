# CI/CD deploy WAR lên AWS EC2

Luồng triển khai dùng GitHub Actions và runner chạy trên EC2. Backend vẫn là
Servlet/JSP/JPA/HikariCP, đóng gói WAR cho Tomcat; không có Spring Boot hoặc
dịch vụ build mới trên production.

## Luồng tự động và thủ công

```text
PR vào main ──> CI trên GitHub-hosted runner
push/merge main hoặc Run workflow (main)
  ──> cùng bộ CI
  ──> WAR + SHA-256 sau khi tất cả kiểm tra đạt
  ──> runner crave-ec2 kiểm tra SHA-256
  ──> migration bổ sung, backup, thay WAR, recreate app
  ──> /crave/health đạt ──> đăng ký webhook payOS
                    lỗi ──> khôi phục WAR/cấu hình trước
```

- [ci.yml](../../.github/workflows/ci.yml) chạy JDK 17, MySQL 8.4 tạm thời,
  Maven `-Pdev clean verify`, các assertion service, kiểm tra JavaScript,
  deploy/rollback fixture, HTTP và browser smoke dùng form thật.
  Ba migration production được áp lại hai lần trong database CI để kiểm tra
  khả năng chạy lặp. Schema/seed chỉ tạo database CI tạm thời.
- Chỉ sau các bước trên, CI tính SHA-256 của WAR và xuất artifact `crave-war`
  gồm `crave.war` và `crave.war.sha256`.
- [deploy.yml](../../.github/workflows/deploy.yml) nhận push/merge `main`
  hoặc `workflow_dispatch`. Chạy thủ công trên nhánh khác `main` sẽ không deploy.
  PR/review không chạy job production.
- CD tải artifact của cùng lần CI và so hash với output tin cậy của job CI.
  Sai hash hoặc CI lỗi thì không gọi script deploy.
- Production dùng runner `[self-hosted, linux, x64, crave-production]`,
  tên `crave-ec2`, environment `production`. Các lần deploy được tuần tự hóa;
  lần đang chạy không bị hủy giữa chừng.

Cấu hình và kiểm thử trong repository không xác nhận runner/secrets trên tài khoản
GitHub đã sẵn sàng. Cần kiểm tra trạng thái trong Settings/Actions của repository
khi triển khai thật.

## EC2 đang chạy

Không ghi đè `.env`, compose hoặc volume database đang dùng. Script
[deploy-ec2.sh](../../scripts/deploy-ec2.sh) đọc compose ở `DEPLOY_PATH`,
bổ sung biến payOS/SMTP/giao hàng qua `.payos.compose.json` riêng và chỉ recreate
service ứng dụng.

Kiểm tra trên EC2:

```bash
cd /home/ubuntu/crave
docker compose --profile production config --services
docker compose --profile production ps
curl --fail http://127.0.0.1:8080/crave/health
```

Compose cần có `app`, `mysql` và bind mount WAR:

```yaml
volumes:
  - ./backend/target/crave.war:/usr/local/tomcat/webapps/crave.war:ro
```

Cần có WAR đang chạy, `.env` và thư mục `backend/target/` để lưu bản rollback.
Deploy directory phải nằm ngoài checkout của GitHub runner.

Runner cần Docker Compose v2, `curl`, `unzip`, `python3`, `flock` và
các công cụ coreutils. Trên Ubuntu có thể cài các tiện ích:

```bash
sudo apt-get update
sudo apt-get install -y curl unzip python3 util-linux coreutils
```

Kiểm tra `docker info` hoặc `sudo -n docker info` bằng tài khoản runner.
Script dùng Docker trực tiếp nếu được phép, hoặc `sudo -n docker`.
Compose cần hỗ trợ `config --format json`, profiles và `up --pull never`.
Runner cần quyền ghi vào riêng thư mục deploy và `backend/target/`.

## EC2 mới, chưa có ứng dụng/database

[compose.yaml](../../compose.yaml) nay có MySQL và Tomcat với profile
`production`:

- `docker compose up -d` tiếp tục chỉ chạy MySQL cho phát triển local.
- `docker compose --profile production up -d` chạy MySQL và WAR trên Tomcat 10.1/JDK 17.
- WAR phải có sẵn; mount không tự tạo thư mục nếu thiếu file.
- MySQL chỉ bind `127.0.0.1:3306`; ứng dụng kết nối MySQL qua mạng Compose.
- `CRAVE_HTTP_PORT` mặc định `8080`; `CRAVE_TOMCAT_IMAGE` có thể chọn image Tomcat phù hợp.

Chỉ thực hiện bootstrap dưới đây với EC2 mới và database trống:

1. Đặt source phát hành trong `/home/ubuntu/crave`, bên ngoài thư mục runner.
2. Tạo `.env` từ `.env.example`, đặt mật khẩu database riêng và thông tin cửa hàng.
   Không đưa `.env` hoặc khóa thật lên Git.
3. Lấy WAR đã qua CI từ artifact `crave-war`, xác minh file SHA-256 rồi đặt vào
   `backend/target/crave.war`.
4. Khởi động:

   ```bash
   cd /home/ubuntu/crave
   docker compose --profile production pull
   docker compose --profile production up -d
   curl --fail http://127.0.0.1:8080/crave/health
   ```

Schema/seed trong Compose chỉ dùng khi MySQL tạo volume trống. Không dùng bước
bootstrap để thay database production. Các lần cập nhật sau dùng CD; script
không chạy lại schema/seed và không recreate service MySQL.

Cho phép cổng web đang sử dụng trong EC2 Security Group, hoặc dùng reverse proxy
80/443. Runner kết nối GitHub qua HTTPS outbound; luồng CD này không yêu cầu
chép SSH private key vào repository.

## Runner GitHub Actions

Vào repository → **Settings → Actions → Runners → New self-hosted runner**,
chọn Linux/x64. Đặt runner ngoài thư mục ứng dụng, ví dụ
`/home/ubuntu/actions-runner`. Dùng các lệnh tải/cấu hình do GitHub cung cấp,
với runner name `crave-ec2` và additional label `crave-production`.

Cài thành service bằng tài khoản vận hành, ví dụ:

```bash
cd /home/ubuntu/actions-runner
sudo ./svc.sh install ubuntu
sudo ./svc.sh start
sudo ./svc.sh status
```

Settings phải hiển thị runner online với đúng tên/labels. Dùng runner hỗ trợ
các action Node.js 24 đã pin trong workflow. Với EC2 ARM64, chọn runner ARM64
và đổi label `x64` của job deploy thành `arm64`.

PR chạy trên runner GitHub-hosted; runner production chỉ dành cho code đã vào
`main`. Nếu dùng repository public hoặc nhận PR ngoài nhóm, giữ chính sách
approval cho workflow không tin cậy và giới hạn runner group phù hợp.

Tham khảo [self-hosted runner](https://docs.github.com/en/actions/reference/runners/self-hosted-runners)
và [cài runner thành service](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application).

## Environment production

Tạo **Settings → Environments → production**, chỉ cho deploy nhánh `main`.
Nếu environment có required reviewers/wait timer, job sẽ chờ chính sách đó;
nếu muốn tự deploy ngay sau merge, cấu hình chính sách tương ứng.

Bắt buộc đặt 5 **Environment secrets**:

| Secret | Nội dung |
|---|---|
| `PAYOS_CLIENT_ID` | Client ID payOS |
| `PAYOS_API_KEY` | API key payOS |
| `PAYOS_CHECKSUM_KEY` | Checksum key payOS |
| `EMAIL_SMTP_PASSWORD` | App password SMTP |
| `EMAIL_OTP_SECRET` | Secret OTP ổn định, ít nhất 32 ký tự ngẫu nhiên |

Bắt buộc đặt **Environment variable** `PAYOS_PUBLIC_BASE_URL`, ví dụ
`https://your-domain.example/crave`. Workflow không mặc định một IP public cũ.
Giá trị gồm context path và được dùng cho return/cancel cũng như webhook.

Các variable còn lại:

| Variable | Mặc định/ý nghĩa |
|---|---|
| `EC2_DEPLOY_PATH` | `/home/ubuntu/crave` |
| `EC2_COMPOSE_FILE` | `compose.yaml`, tương đối trong thư mục deploy |
| `EC2_APP_SERVICE` | `app` |
| `EC2_DB_SERVICE` | `mysql`; container có `MYSQL_ROOT_PASSWORD` để áp migration |
| `EC2_HEALTH_URL` | `http://127.0.0.1:8080/crave/health` |
| `EMAIL_SMTP_HOST`, `EMAIL_SMTP_PORT` | `smtp.gmail.com`, `587` |
| `EMAIL_SMTP_USERNAME`, `EMAIL_FROM` | Mặc định địa chỉ dự án trong workflow; phải khớp app password |
| `CRAVE_STORE_ADDRESS` | Địa chỉ cửa hàng; tùy chọn nếu compose/.env đã có |
| `CRAVE_STORE_LATITUDE`, `CRAVE_STORE_LONGITUDE` | Đặt cùng nhau khi đổi ghim cửa hàng |
| `CRAVE_GEOCODER_URL` | `https://photon.komoot.io/api/`, hỗ trợ Photon GeoJSON |

Trước lần phát hành này, nếu GitHub environment/repository đang có
`CRAVE_GEOCODER_URL` trỏ tới Nominatim (`.../search`), xóa variable đó để dùng
mặc định Photon hoặc đổi sang endpoint Photon tương thích. Variable GitHub
được ưu tiên hơn giá trị mặc định; WAR hiện tại đọc Photon GeoJSON. Không đổi
DNS hệ điều hành. `.env` cũ trên EC2 vẫn được giữ nguyên, còn workflow truyền
endpoint Photon qua override riêng khi deploy.

Production dùng STARTTLS. Secrets chỉ truyền vào bước cần dùng, không đưa vào
WAR hoặc command line. File override và bản backup chứa cấu hình có quyền `0600`.
Biến của workflow không thay đổi cách compose gốc nội suy database từ `.env`;
override chỉ áp các setting tích hợp đã kiểm tra.

## Cập nhật và rollback

Push/merge vào `main` để chạy tự động. Để triển khai lại thủ công, vào
**Actions → CI/CD - main to EC2 → Run workflow**, chọn `main`.
Cả hai cách đều chạy lại CI, không bỏ qua kiểm thử.

Script thực hiện:

1. Xác minh WAR là ZIP hợp lệ, kiểm tra đường dẫn và khóa deploy.
2. Kiểm tra cấu hình, áp các migration bổ sung payOS, OTP và tọa độ giao hàng
   bằng MySQL client trong container. Mật khẩu lấy bên trong container.
3. Lưu WAR cùng trạng thái cấu hình hiện tại vào `.deploy-backups/`.
4. Thay WAR bằng rename, kích hoạt override rồi recreate chỉ `app`.
5. Health phải trả HTTP 200 và `application=UP`/`database=UP`. Nếu lỗi,
   khôi phục WAR/cấu hình cũ; chỉ khi rollback mới thử thêm alias `/api/health`
   cho phiên bản JSON trước.
6. Khi ứng dụng khỏe, workflow đăng ký
   `PAYOS_PUBLIC_BASE_URL/api/payos/webhook` và ghi commit + SHA-256 trong summary.

Recreate Tomcat có gián đoạn ngắn và session hiện tại kết thúc. Migration bổ sung
được giữ khi rollback; không xóa dữ liệu hoặc database. Nếu webhook đăng ký lỗi,
workflow báo lỗi ở bước đó nhưng WAR đã khỏe vẫn chạy. Summary ghi riêng trạng
thái deploy và webhook kể cả khi bước webhook lỗi. Log đăng ký webhook phân loại
lỗi mạng theo từng lần thử, không in khóa hay nội dung phản hồi. Dựa vào loại lỗi
để sửa kết nối/cấu hình rồi chạy lại; không kết luận sai keys chỉ từ lỗi mạng.

Script đăng ký webhook cho phép timeout 60 giây mỗi lần gọi và thử tối đa ba lần.
payOS còn gửi callback mẫu tới ứng dụng trong bước xác minh URL; mức 60 giây
phù hợp timeout mặc định của [SDK Python chính thức](https://github.com/payOSHQ/payos-lib-python/blob/main/src/payos/_client.py#L64-L66).
Timeout này chỉ áp dụng cho script đăng ký webhook của CD.

## Chẩn đoán nhanh

| Hiện tượng | Kiểm tra |
|---|---|
| Job chờ runner | Tên `crave-ec2`, labels đúng, service runner online |
| Job chờ approval | Chính sách environment `production` |
| Thiếu public URL/secrets | Variables/secrets của environment, không chỉ repository local |
| Sai SHA-256 | Artifact phải thuộc cùng CI; không thay file WAR sau CI |
| Thiếu WAR/service/mount | Compose production và `backend/target/crave.war` |
| Image chưa có ở EC2 | Pull image trong bootstrap; deploy không tự pull khi thay WAR |
| Permission denied | Quyền ghi thư mục deploy/target của tài khoản runner |
| Migration lỗi | Service MySQL đang chạy, quyền root container; WAR cũ chưa bị thay |
| Health lỗi | `docker compose --profile production logs --tail=100 app` |
| Webhook lỗi HTTP hoặc bị từ chối | Public URL, cổng web/HTTPS và keys payOS |
| Webhook lỗi timeout/DNS/TLS/kết nối | Kết nối HTTPS outbound từ runner EC2 tới `api-merchant.payos.vn:443`; xem loại lỗi từng lần thử |

Không chạy `database/schema.sql` trên database đang có dữ liệu để sửa lỗi CD.
Để kiểm tra kết nối từ chính runner mà không gửi khóa hoặc đổi webhook:

```bash
curl -I --connect-timeout 5 --max-time 15 -A 'CraveWebhookRegistrar/1.0' \
  -w '\nHTTP=%{http_code} total=%{time_total}s\n' \
  https://api-merchant.payos.vn/confirm-webhook
```

Lệnh HEAD này kiểm tra kết nối DNS/TCP/TLS/HTTP; phản hồi 4xx vẫn cho thấy đã
kết nối tới máy chủ. Nó không xác nhận đăng ký webhook hoặc tính đúng của keys.

Kiểm thử deploy/rollback nằm tại `tests/scripts/deploy-ec2-test.sh`; tất cả dữ liệu
của fixture tách khỏi production.
