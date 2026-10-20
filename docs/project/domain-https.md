# Chuyển EC2 sang https://craveshop.duckdns.org

Mục tiêu: mở trang chủ bằng `https://craveshop.duckdns.org`, bỏ cổng `8080` và
tiền tố `/crave`. Các trang con vẫn có đường dẫn riêng, ví dụ `/menu`.

Thông tin server đã xác định:

| Thành phần | Giá trị |
|---|---|
| OS | Ubuntu 24.04 |
| DuckDNS A record | `craveshop.duckdns.org` → `15.134.39.86` |
| Thư mục triển khai | `/home/ubuntu/crave` |
| Compose | `compose.yaml` và `.payos.compose.json` |
| Service app | `app` |
| WAR hiện tại | `backend/target/crave.war` |

Các lệnh Linux dưới đây chạy trong **terminal EC2**, không chạy trong PowerShell
Windows. File cấu hình trong repository chỉ được áp dụng lên server khi thực
hiện các bước dưới đây. Giữ nguyên database và các secret đang dùng.

## 1. Chuẩn bị CI/CD cho ROOT

Phiên bản cũ của script deploy chỉ chấp nhận `/crave`. Đưa các thay đổi ở
`scripts/deploy-ec2.sh`, `scripts/register-payos-webhook.py` và các test tương ứng
vào `main` trước lần chạy workflow tiếp theo khi server đã dùng ROOT. Phiên bản
mới vẫn hỗ trợ cách chạy `/crave` hiện tại; source artifact vẫn tên `crave.war`.

Trong lúc thực hiện các bước đổi cấu hình, không chạy workflow deploy khác.

## 2. Mở cổng và cài Nginx/Certbot

Trên AWS: **EC2 → Instance → Security → Security Group → Edit inbound rules**:

| Type | Port | Source |
|---|---|---|
| HTTP | 80 | `0.0.0.0/0` |
| HTTPS | 443 | `0.0.0.0/0` |

Chạy trên EC2; có thể chạy lại nếu đã cài:

```bash
sudo apt-get update
sudo apt-get install -y nginx certbot python3-certbot-nginx
sudo systemctl enable --now nginx
sudo ufw status
```

Nếu UFW trả về `Status: active`, chạy `sudo ufw allow 'Nginx Full'`.
Nếu UFW đang tắt, giữ nguyên trạng thái đó.

## 3. Sao lưu cấu hình hiện tại

Chạy một lần trước khi sửa cấu hình:

```bash
cd /home/ubuntu/crave
install -d -m 700 .deploy-backups
backup_dir=$(mktemp -d /home/ubuntu/crave/.deploy-backups/before-https.XXXXXX)
cp -p compose.yaml "$backup_dir/compose.yaml"
cp -p .payos.compose.json "$backup_dir/.payos.compose.json"
cp -p .env "$backup_dir/.env"
printf 'Backup: %s\n' "$backup_dir"
```

Ghi lại đường dẫn backup được in ra để dùng khi cần khôi phục.

## 4. Tạo cấu hình Tomcat cho HTTPS

Chạy nguyên khối này trên EC2:

```bash
cd /home/ubuntu/crave
mkdir -p deploy/tomcat
cat > deploy/tomcat/server-https.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1">
    <Listener className="org.apache.catalina.startup.VersionLoggerListener" />
    <Listener className="org.apache.catalina.core.JreMemoryLeakPreventionListener" />
    <Listener className="org.apache.catalina.core.ThreadLocalLeakPreventionListener" />
    <Service name="Catalina">
        <Connector port="8080" protocol="HTTP/1.1"
                   connectionTimeout="20000" URIEncoding="UTF-8"
                   scheme="https" secure="true"
                   proxyName="craveshop.duckdns.org" proxyPort="443" />
        <Engine name="Catalina" defaultHost="localhost">
            <Host name="localhost" appBase="webapps"
                  unpackWARs="true" autoDeploy="false">
                <Valve className="org.apache.catalina.valves.AccessLogValve"
                       directory="logs" prefix="localhost_access_log" suffix=".txt"
                       pattern="%h %l %u %t &quot;%r&quot; %s %b" />
            </Host>
        </Engine>
    </Service>
</Server>
XML
```

Connector khai báo đúng hostname, HTTPS và cổng 443 để kiểm tra Origin khi gửi
form và cookie đăng nhập nhận đúng địa chỉ public. Nginx kết nối tới Tomcat bằng
HTTP nội bộ. Cấu hình này dành riêng cho tên miền trên.

## 5. Đổi service app sang ROOT

Mở **Compose hiện có trên EC2**:

```bash
nano /home/ubuntu/crave/compose.yaml
```

Ở service `app`, thay phần `ports` và mount WAR, rồi thêm mount `server.xml`:

```yaml
    ports:
      - "127.0.0.1:8080:8080"
    volumes:
      - ./backend/target/crave.war:/usr/local/tomcat/webapps/ROOT.war:ro
      - ./deploy/tomcat/server-https.xml:/usr/local/tomcat/conf/server.xml:ro
```

Nếu mount dùng cấu trúc dài, chỉ đổi `target` của WAR từ
`/usr/local/tomcat/webapps/crave.war` sang `/usr/local/tomcat/webapps/ROOT.war`,
giữ nguyên `source`, `read_only`, `bind`, rồi thêm mount cấu hình. Không giữ đồng
thời cả mount WAR cũ và mount ROOT. Giữ nguyên các mount khác nếu có.

Lưu bằng **Ctrl+O → Enter → Ctrl+X**. Không thay image, cấu hình MySQL, environment
hay volume dữ liệu. Không chép đè Compose production bằng bản local vì đường
dẫn WAR của hai môi trường khác nhau.

## 6. Đổi URL payOS và khởi động lại app

Trong GitHub → **Settings → Environments → production → Variables**, đặt:

| Variable | Giá trị |
|---|---|
| `EC2_HEALTH_URL` | `http://127.0.0.1:8080/health` |
| `PAYOS_PUBLIC_BASE_URL` | `https://craveshop.duckdns.org` |

Giữ nguyên các secret. Sau đó chạy trên EC2 để đổi URL đang dùng; lệnh này không
in thông tin payOS hoặc SMTP:

```bash
cd /home/ubuntu/crave
python3 - <<'PY'
import json
import re
from pathlib import Path

url = 'https://craveshop.duckdns.org'
override = Path('.payos.compose.json')
config = json.loads(override.read_text(encoding='utf-8'))
config['services']['app']['environment']['PAYOS_PUBLIC_BASE_URL'] = url
env_path = Path('.env')
env_text = env_path.read_text(encoding='utf-8')
pattern = r'(?m)^[ \t]*(?:export[ \t]+)?PAYOS_PUBLIC_BASE_URL[ \t]*=.*$'
if re.search(pattern, env_text):
    env_text = re.sub(pattern, 'PAYOS_PUBLIC_BASE_URL=' + url, env_text)
else:
    env_text = env_text.rstrip('\n') + '\nPAYOS_PUBLIC_BASE_URL=' + url + '\n'
override.write_text(json.dumps(config, ensure_ascii=True) + '\n', encoding='utf-8')
override.chmod(0o600)
env_path.write_text(env_text, encoding='utf-8')
env_path.chmod(0o600)
PY

sudo docker compose --profile production -f compose.yaml -f .payos.compose.json config --quiet
```

Nếu bước kiểm tra báo lỗi, sửa lỗi đó trước khi chạy tiếp. Nếu không có lỗi:

```bash
sudo docker compose --profile production -f compose.yaml -f .payos.compose.json \
  up -d --no-deps --force-recreate --no-build --pull never app
curl --fail --retry 30 --retry-all-errors --retry-delay 2 --max-time 5 \
  http://127.0.0.1:8080/health
```

Health phải trả về `application=UP` và `database=UP`. Nếu chưa đạt, xem log và xử
lý trước khi tiếp tục:

```bash
sudo docker compose --profile production -f compose.yaml -f .payos.compose.json logs --tail=80 app
```

Lệnh trên chỉ recreate app, không khởi tạo lại database. Không thử đăng nhập
bằng HTTP public vì Tomcat hiện được cấu hình cho HTTPS.

## 7. Tạo Nginx và cấp chứng chỉ

File cấu hình mẫu nằm tại [craveshop.conf](../../deploy/nginx/craveshop.conf).
Để tạo trực tiếp trên server, chạy khối dưới đây **một lần**. Nếu đã có cấu hình
Nginx cho tên miền này, giữ bản sao trước khi sửa; không chép đè cấu hình HTTPS
đang hoạt động bằng mẫu HTTP.

```bash
sudo tee /etc/nginx/sites-available/craveshop > /dev/null <<'NGINX'
server {
    listen 80;
    server_name craveshop.duckdns.org;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_redirect off;
    }
}
NGINX

if [ ! -e /etc/nginx/sites-enabled/craveshop ] && [ ! -L /etc/nginx/sites-enabled/craveshop ]; then
    sudo ln -s /etc/nginx/sites-available/craveshop /etc/nginx/sites-enabled/craveshop
fi
sudo nginx -t
```

Nếu `nginx -t` thành công, chạy:

```bash
sudo systemctl reload nginx
sudo certbot --nginx -d craveshop.duckdns.org --redirect
```

Nhập email và chấp nhận điều khoản theo hướng dẫn của Certbot. Certbot bổ sung
cấu hình TLS/443 và redirect từ HTTP sang HTTPS. HTTP-01 cần bản ghi DNS đúng
và cổng 80 truy cập được từ Internet. Nếu lỗi timeout khi cấp chứng chỉ, kiểm
tra lại Security Group, DNS và firewall trước khi thử lại.

## 8. Kiểm tra

```bash
curl --fail https://craveshop.duckdns.org/health
curl -I http://craveshop.duckdns.org/
sudo certbot renew --dry-run
```

Mở **https://craveshop.duckdns.org**, kiểm tra CSS/ảnh, đăng nhập, đăng xuất và
giỏ hàng. Cookie phiên phải có `Path=/`, `Secure`, `HttpOnly`; form POST HTTPS
phải hoạt động. Trang chủ dùng `/`; các trang con dùng `/menu`, `/auth/login`,
v.v. mà không có tiền tố `/crave`.

Chạy workflow trên `main` để xác nhận deploy với health mới và đăng ký webhook
`https://craveshop.duckdns.org/api/payos/webhook`. Chỉ sửa `.env` không đủ vì
workflow quản lý override và đăng ký webhook từ variable trên GitHub.

Sau khi xác nhận HTTPS hoạt động, có thể xóa inbound rule public 8080.

## Khôi phục khi đổi cấu hình gặp lỗi

Đặt `backup_dir` thành đúng đường dẫn đã ghi ở bước 3, rồi chép lại ba file:

```bash
cd /home/ubuntu/crave
cp -p "$backup_dir/compose.yaml" compose.yaml
cp -p "$backup_dir/.payos.compose.json" .payos.compose.json
cp -p "$backup_dir/.env" .env
sudo docker compose --profile production -f compose.yaml -f .payos.compose.json \
  up -d --no-deps --force-recreate --no-build --pull never app
curl --fail --retry 30 --retry-all-errors --retry-delay 2 --max-time 5 \
  http://127.0.0.1:8080/crave/health
```

Khôi phục cả hai variable GitHub về URL/health cũ trước khi deploy tiếp.
Nếu đã đóng cổng public 8080, mở lại rule ban đầu để dùng địa chỉ cũ. Nginx
vẫn proxy tới ROOT nên khôi phục app `/crave` không tự khôi phục trang chủ
HTTPS. Nếu webhook đã đổi, chạy workflow với cấu hình cũ để đăng ký lại webhook
cũ. Không xóa volume database.

Tham khảo: [Nginx proxy](https://nginx.org/en/docs/http/ngx_http_proxy_module.html),
[Tomcat proxy](https://tomcat.apache.org/tomcat-10.1-doc/proxy-howto.html),
[Certbot Nginx](https://eff-certbot.readthedocs.io/en/stable/using.html#nginx),
[Let's Encrypt HTTP-01](https://letsencrypt.org/docs/challenge-types/#http-01-challenge).
