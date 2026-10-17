# Xác thực email và đổi mật khẩu

Ứng dụng Servlet gửi mã sáu chữ số qua Jakarta Mail/Eclipse Angus, không dùng
Spring hoặc framework ứng dụng. Đăng ký khách hàng phải xác thực email trước
khi tạo tài khoản. Khách hàng, nhân viên và quản trị viên có thể đặt lại mật khẩu
bằng email; tài khoản đã đăng nhập có thể đổi mật khẩu sau khi xác nhận mật khẩu
hiện tại và mã gửi đến email của chính tài khoản đó.

| Servlet (form POST) | Tham số form |
|---|---|
| `POST /auth/register/code` | `email` |
| `POST /auth/register/submit` | Các trường đăng ký và `verificationCode` |
| `POST /auth/password/reset/code` | `email` |
| `POST /auth/password/reset/submit` | `email`, `verificationCode`, `password`, `confirmPassword` |
| `POST /auth/password/change/code` | `currentPassword`; cần đăng nhập |
| `POST /auth/password/change/submit` | `verificationCode`, `password`, `confirmPassword`; cần đăng nhập |

Mã có hiệu lực 10 phút, chỉ dùng một lần, gắn với email và mục đích. Mỗi mã
cho phép tối đa năm lần nhập sai. Gửi lại cách nhau ít nhất 60 giây và tối đa
năm lần trong cửa sổ một giờ. Database lưu digest HMAC với salt và secret riêng,
không lưu mã thô; JSP và log không trả mã. Luồng quên mật khẩu trả phản hồi chung
cho email không tồn tại hoặc tài khoản không hoạt động. Đặt lại/đổi thành công
thu hồi các phiên đăng nhập cũ bằng phiên bản mật khẩu; người dùng đăng nhập lại.

## Cấu hình gửi email

`.env` bị Git bỏ qua. Các giá trị môi trường được ưu tiên hơn JVM properties và
`.env` ở thư mục dự án. Cấu hình được đọc khi dùng tính năng email, không cản ứng
dụng khởi động khi email chưa được cấu hình.

| Biến | Giá trị/ý nghĩa |
|---|---|
| `EMAIL_SMTP_HOST` | `smtp.gmail.com` |
| `EMAIL_SMTP_PORT` | `587` |
| `EMAIL_SMTP_USERNAME` | `craveshopvn@gmail.com` |
| `EMAIL_SMTP_PASSWORD` | Google app password; chỉ lưu local hoặc GitHub Environment secret |
| `EMAIL_FROM` | `craveshopvn@gmail.com` |
| `EMAIL_SMTP_STARTTLS` | `true` |
| `EMAIL_OTP_SECRET` | Ít nhất 32 ký tự ngẫu nhiên, giữ ổn định qua các lần khởi động |

SMTP bắt buộc STARTTLS, kiểm tra danh tính/certificate máy chủ và dùng TLS 1.2
hoặc 1.3. Timeout kết nối, đọc và ghi đều là 10 giây. Lỗi gửi mail được chuyển
thành thông báo chung, không đính kèm lỗi nhà cung cấp hoặc credentials. Gmail
sử dụng email đầy đủ và app password theo [hướng dẫn Google](https://support.google.com/mail/answer/7104828?hl=en).
Dependency `org.eclipse.angus:jakarta.mail:2.0.5` là bản phát hành ổn định được
xác minh từ [Eclipse Angus](https://github.com/eclipse-ee4j/angus-mail/releases).
Các thiết lập TLS/timeout theo [tài liệu SMTP Angus](https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html).

Migration `database/migrations/2026-10-13-email-verification.sql` thêm
`email_verification_challenges` và `user_account.password_version`. Deploy EC2
áp migration trước khi cài WAR; rollback giữ bảng bổ sung và khôi phục cấu hình
trước đó. Không copy `.env` local lên EC2. Workflow nhận SMTP password và OTP
secret từ environment `production`, lưu private Compose override với quyền 0600.

## Kiểm thử không gửi email thật

Backend test dùng sender giả lập. Packaged-WAR HTTP smoke dùng
`tests/runtime/smtp-fixture.cjs`, listener SMTP ở `127.0.0.1:2525` và inbox HTTP
ở `127.0.0.1:2526`. Mã chỉ tồn tại trong bộ nhớ của fixture, không in ra console
hoặc đưa vào API production. Plaintext SMTP chỉ được phép với địa chỉ loopback.

CI thiết lập riêng `EMAIL_SMTP_HOST=127.0.0.1`, `EMAIL_SMTP_PORT=2525`,
`EMAIL_SMTP_STARTTLS=false`, username/password rỗng, địa chỉ gửi test và một
OTP secret giả lập. Browser smoke `tests/frontend/servlet-form-browser-smoke.cjs`
lấy mã từ inbox local và gửi các form thật để kiểm tra đăng ký, reset/change và
thu hồi phiên. Backend test kiểm tra mã sai/tái sử dụng/sai mục đích cho các loại
tài khoản. Không chạy smoke này với database hoặc Gmail production.
