# Thanh toán QR qua payOS

Ứng dụng dùng Java 17, Jakarta Servlet và JSP. `PayosPaymentServlet` phục vụ
tạo link, kiểm tra trạng thái và ảnh QR; `PayosWebhookServlet` nhận xác nhận từ
payOS. Client gọi API bằng `java.net.http.HttpClient`, ảnh QR được tạo cục bộ
bằng ZXing. Không gửi khóa API hoặc checksum key ra trình duyệt.

## Cấu hình và chạy local

Thêm các biến vào `.env` ở gốc repository hoặc môi trường của Tomcat:

```dotenv
PAYOS_CLIENT_ID=<client-id>
PAYOS_API_KEY=<api-key>
PAYOS_CHECKSUM_KEY=<checksum-key>
PAYOS_PUBLIC_BASE_URL=http://localhost:8081/crave
```

`.env` bị Git bỏ qua; `.env.example` chỉ chứa tên biến và giá trị mẫu.
Biến môi trường có ưu tiên cao hơn `.env`. Base URL phải gồm context path
`/crave` khi ứng dụng được deploy dưới tên WAR này.

Chạy migration bổ sung bằng tài khoản MySQL có quyền tạo bảng. Migration
chỉ thêm bảng `payos_payment_link`, giữ nguyên dữ liệu đơn hàng và thanh toán:

```powershell
mysql --user=root --password --database=crave --execute="SOURCE database/migrations/2026-10-13-payos-payment.sql"
powershell -ExecutionPolicy Bypass -File scripts/dev/run.ps1 -Port 8081
```

Sau khi thay cấu hình, khởi động lại ứng dụng. Khi đặt hàng, chọn **Chuyển khoản
ngân hàng** hoặc **Ví điện tử**; trang chi tiết đơn hiển thị QR, số tiền,
tài khoản nhận và nội dung chuyển khoản. Dùng ứng dụng ngân hàng hoặc ví có
hỗ trợ VietQR. Nút mở trang payOS thuận tiện khi thanh toán trên cùng điện thoại.

Localhost không thể nhận callback trực tiếp từ Internet. Trong lúc chạy local,
trang chi tiết đơn kiểm tra trạng thái qua server mỗi 5 giây khi đang mở và
chưa thanh toán; server xác nhận lại với payOS. Đóng trang không làm mất link
thanh toán, nhưng cần mở lại trang để đồng bộ nếu chưa cấu hình webhook public.

## Khi có URL public

Đổi `PAYOS_PUBLIC_BASE_URL` sang URL ứng dụng public và khởi động lại WAR.
Đăng ký endpoint sau trong kênh thanh toán trên payOS:

```text
POST <PAYOS_PUBLIC_BASE_URL>/api/payos/webhook
```

Webhook yêu cầu chữ ký HMAC-SHA256 đúng checksum key. Servlet xác thực dữ liệu
rồi hỏi lại trạng thái link từ payOS trước khi ghi nhận đủ tiền.
Các tham số `payment=return`, `payment=cancel` trên URL trình duyệt chỉ phục vụ
điều hướng, không tự thay đổi trạng thái thanh toán hay hủy đơn.

## API của ứng dụng

| Endpoint | Hành vi |
|---|---|
| `POST /api/payos/orders/{orderId}` | Khách hàng tạo hoặc lấy lại link của đơn mình; không nhận số tiền từ client |
| `GET /api/payos/orders/{orderId}` | Đồng bộ trạng thái từ payOS, trả thông tin link và thanh toán |
| `GET /api/payos/orders/{orderId}/qr` | Ảnh PNG của dữ liệu QR đã lưu; yêu cầu đăng nhập và quyền sở hữu đơn |
| `POST /api/payos/webhook` | Callback có chữ ký, không yêu cầu session trình duyệt |

Một thanh toán có một mã payOS được lưu bền vững trước khi gọi mạng. Request
lặp hoặc timeout dùng lại mã đó. Đơn tiền mặt không tạo link. Link đã hủy/hết
hạn không được mở lại; khách hàng liên hệ nhà hàng hoặc đặt đơn mới.

Thanh toán một phần giữ trạng thái chờ; chỉ trạng thái `PAID` với tổng tiền
đã nhận đủ và đúng số tiền gốc mới ghi `SUCCESS`. Webhook lặp không ghi nhận
hai lần, và không hạ trạng thái thanh toán thành công/đã hoàn tiền. Khi hủy đơn,
ứng dụng đồng bộ hủy link; nếu tiền đến sau khi đơn bị hủy, ghi nhận khoản tiền
thực nhận để nhà hàng xử lý hoàn tiền, đồng thời giữ đơn ở trạng thái hủy.
Ứng dụng chưa tự chuyển tiền hoàn trả qua payOS.

## Kiểm thử

```powershell
mvn -f backend/pom.xml -Pdev verify
node tests/frontend/payos-payment-browser-smoke.cjs
```

Gateway tests dùng HTTP fixture có chữ ký; service tests kiểm tra quyền, số tiền,
request đồng thời, webhook và các trạng thái thanh toán. Không tự chuyển tiền
thật trong kiểm thử.

Tham khảo [API payOS](https://payos.vn/docs/api/),
[xác thực chữ ký](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/).
