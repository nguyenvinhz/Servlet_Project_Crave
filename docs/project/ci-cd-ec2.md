# CI/CD từ main lên EC2

Push hoặc merge vào `main` sẽ chạy CI, sau đó deploy WAR đã kiểm thử lên EC2.
Pull request vào `main` chỉ chạy CI. Ứng dụng hiện có ở `/home/ubuntu/crave`,
chạy bằng Docker Compose với service `app` và MySQL.

## Luồng chạy

```text
Pull request vào main ──> CI trên GitHub runner

Push/merge main ──> CI trên GitHub runner ──> artifact crave-war
                                           │
                                           v
                           Runner EC2: backup WAR, thay WAR,
                           recreate app, kiểm tra /api/health
                                           │
                              thất bại ──> khôi phục WAR cũ
```

- [ci.yml](../../.github/workflows/ci.yml): JDK 17, MySQL 8.4 tạm thời, Maven
  `clean verify`, assertion giỏ hàng/voucher, kiểm tra cú pháp JavaScript,
  kiểm thử trình duyệt và kiểm thử script deploy/rollback. Schema/seed chỉ được
  chạy trong database CI tạm thời.
- [deploy.yml](../../.github/workflows/deploy.yml): gọi CI, tải đúng artifact của
  cùng lần chạy rồi gọi [deploy-ec2.sh](../../scripts/deploy-ec2.sh) trên EC2.
- CD chỉ recreate service ứng dụng. Database, volume MySQL, `.env` và compose
  trên server được giữ nguyên. Migration database cần kế hoạch riêng.
- Các lần deploy được tuần tự hóa và có khóa trên server. Lần đang deploy được
  chạy đến hết; GitHub có thể thay lần đang chờ bằng push mới nhất.

## Thiết lập một lần trên EC2

Runner EC2 nhận job từ GitHub qua HTTPS outbound 443. Giữ cấu hình SSH hiện có;
không cần thêm SSH key vào GitHub Secrets. Xem [kết nối self-hosted runner](https://docs.github.com/en/actions/reference/runners/self-hosted-runners).

### 1. Kiểm tra môi trường đang deploy

SSH vào EC2 bằng tài khoản `ubuntu`:

```bash
cd /home/ubuntu/crave
sudo docker compose config --services
sudo docker compose ps
curl --fail http://127.0.0.1:8080/crave/api/health
```

Cần có service `app`, `.env`, `backend/target/crave.war` và volume của `app`:

```yaml
volumes:
  - ./backend/target/crave.war:/usr/local/tomcat/webapps/crave.war:ro
```

Đây là cấu hình đang dùng từ bước deploy trước. Nếu cần sửa volume, thêm vào
service `app` của compose trên EC2. Compose trong repo phục vụ MySQL môi trường
phát triển, nên không copy đè compose production.

Cài các công cụ mà script cần:

```bash
sudo apt-get update
sudo apt-get install -y curl unzip python3 util-linux coreutils
```

Runner cần quyền ghi trong `/home/ubuntu/crave/backend/target` và tạo file trong
`/home/ubuntu/crave`. Nếu WAR đã được chép bằng `sudo` và các thư mục này đang
thuộc `root`, sửa quyền sở hữu của riêng thư mục deploy/target:

```bash
sudo chown ubuntu:ubuntu /home/ubuntu/crave /home/ubuntu/crave/backend/target
```

Script dùng `docker` trực tiếp nếu truy cập được daemon, hoặc `sudo -n docker`
nếu tài khoản runner có quyền sudo không cần nhập mật khẩu. Kiểm tra bằng
`docker info` hoặc `sudo -n docker info`. Docker Compose v2 cần hỗ trợ
`config --format json` và `up --pull never`.

### 2. Thêm GitHub Actions runner

Mở repository → **Settings → Actions → Runners → New self-hosted runner**.
Chọn **Linux / x64** tương ứng với EC2 đã deploy.

Trên EC2, tạo thư mục runner riêng, bên ngoài thư mục ứng dụng:

```bash
mkdir -p /home/ubuntu/actions-runner
cd /home/ubuntu/actions-runner
```

Chạy các lệnh tải, giải nén và `./config.sh` do trang GitHub cung cấp, bằng
tài khoản `ubuntu`. Khi cấu hình:

- Runner name: `crave-ec2`.
- Additional labels: `crave-production` (giữ các label mặc định).
- Work folder: dùng `_work` mặc định.

Token đăng ký runner chỉ dùng trong lệnh cấu hình, không đưa vào repo.
Dùng bản runner mới nhất trên trang GitHub; các action hiện tại yêu cầu runner
hỗ trợ Node.js 24. Nếu EC2 là ARM64, chọn ARM64 trong trang cài đặt và đổi label
`x64` thành `arm64` trong `deploy.yml`.

Cài runner thành service để hoạt động sau khi đóng SSH hoặc reboot:

```bash
cd /home/ubuntu/actions-runner
sudo ./svc.sh install ubuntu
sudo ./svc.sh start
sudo ./svc.sh status
```

GitHub phải hiển thị runner **Idle** với label `crave-production`.
Tham khảo [thêm runner](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners)
và [cài runner thành service](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/configure-the-application).

Runner này có quyền deploy production: chỉ dùng label `crave-production` trong
job CD của `main`. Các job pull request/fork chạy trên `ubuntu-24.04` của GitHub.
Với repository public, GitHub giải thích rủi ro chạy code không tin cậy trên
runner riêng trong [hướng dẫn bảo mật runner](https://docs.github.com/en/actions/reference/security/secure-use).
Trong **Settings → Actions → General → Approval for running fork pull request
workflows from contributors**, chọn yêu cầu approval cho **all outside
collaborators**. Đọc thay đổi workflow trước khi duyệt: một PR có thể thêm job
khác dùng runner production, nên điều kiện `main` của CD chỉ bảo vệ workflow
hiện có. Nếu dùng runner group của organization, giới hạn quyền sử dụng vào
workflow deploy trên `main`.

### 3. Environment production

Vào **Settings → Environments → New environment**, tạo `production`.
Trong **Deployment branches and tags**, chọn **Selected branches and tags**,
thêm branch `main`.

Nếu muốn deploy tự động sau mỗi push, không bật required reviewers cho
environment này. Branch protection có thể yêu cầu CI thành công trước khi merge.

Với cấu hình hiện tại, không cần tạo secret hay variable: các giá trị mặc định
đã khớp EC2. Nếu thay đổi server, thêm **Environment variables**:

| Variable | Mặc định | Ý nghĩa |
|---|---|---|
| `EC2_DEPLOY_PATH` | `/home/ubuntu/crave` | Thư mục ứng dụng trên EC2, nằm ngoài checkout runner |
| `EC2_COMPOSE_FILE` | `compose.yaml` | File compose, đường dẫn tương đối trong thư mục ứng dụng |
| `EC2_APP_SERVICE` | `app` | Tên service Tomcat |
| `EC2_HEALTH_URL` | `http://127.0.0.1:8080/crave/api/health` | Endpoint health trên máy EC2 |

## Bật workflow và kiểm tra lần đầu

Đưa thay đổi CI/CD qua pull request vào `main`. Khi merge, workflow
**CI/CD - main to EC2** tự chạy. Xem tab **Actions**:

1. `CI / Build and test` phải thành công và tạo artifact `crave-war`.
2. `Deploy EC2` lấy artifact, lưu WAR hiện tại vào
   `/home/ubuntu/crave/.deploy-backups/crave.war.previous`, thay WAR và recreate `app`.
3. Endpoint health phải trả HTTP 200 với `success: true`,
   `data.application: "UP"`, `data.database: "UP"` trong 180 giây.
4. Khi thành công, summary ghi commit đã deploy. Mở website để kiểm tra chức năng.

Có thể chạy lại bằng **Actions → CI/CD - main to EC2 → Run workflow → main**.
Chạy thủ công ở branch khác sẽ bỏ qua cả CI và CD của workflow deploy.

Nếu recreate hoặc health check thất bại, script tự khôi phục WAR cũ và recreate
`app` lần nữa. Workflow vẫn báo lỗi để người triển khai kiểm tra. Mỗi deploy
recreate Tomcat nên có khoảng gián đoạn ngắn và session đang đăng nhập sẽ mất.
Rollback khi force kill, hủy job hoặc EC2 crash có thể chưa hoàn tất; dùng backup
trên server để khôi phục thủ công khi cần.
Health endpoint xác nhận trạng thái database lúc khởi động, không thay thế
giám sát database liên tục.

## Khi job không chạy được

| Hiện tượng | Cách kiểm tra |
|---|---|
| `Deploy EC2` đang chờ runner | Runner phải online, đúng label `self-hosted`, `linux`, `x64`, `crave-production`; xem `sudo ./svc.sh status` |
| Job chờ approval | Kiểm tra required reviewers của environment `production` |
| Không truy cập Docker | Thử `sudo -n docker info` dưới tài khoản `ubuntu` của service runner |
| Báo thiếu app bind mount | Kiểm tra service `app` trong compose production và đường dẫn WAR ở bước 1 |
| Permission denied khi backup/thay WAR | Kiểm tra quyền thư mục deploy và `backend/target`; WAR được thay bằng rename nên cần quyền ghi thư mục |
| Health check thất bại | Trên EC2: `cd /home/ubuntu/crave`, `sudo docker compose ps`, `sudo docker compose logs --tail=100 app` |

Không chạy `database/schema.sql` trên database production để sửa lỗi deploy:
script này có `DROP DATABASE`. CD không tự áp dụng schema/seed.
