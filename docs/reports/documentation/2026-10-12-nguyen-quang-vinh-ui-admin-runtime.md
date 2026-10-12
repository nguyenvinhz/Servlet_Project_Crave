# Hoàn thiện giao diện, quản trị và chạy dự án

Ngày kiểm tra: 2026-10-12. Nhánh: `fix/admin-ui-project-review`.

## Phạm vi đã thực hiện

- Cập nhật từ `origin/main` tại commit `5a3411f`. Giữ và chỉnh các thành phần đã có; mẫu Framer dùng để tham khảo màu sắc, bố cục và hình ảnh.
- Việt hóa nội dung, sửa encoding JSP, thống nhất định dạng tiền/ngày, sửa responsive và trạng thái lỗi/tải lại cho menu, ưu đãi, tài khoản, giỏ hàng, checkout và đơn hàng.
- Tạo shell quản trị theo mẫu `food-admin-dashboard`: tổng quan, đơn hàng, món ăn, danh mục, khách hàng, nhân viên, khuyến mãi và báo cáo.
- Tổng quan và báo cáo lấy dữ liệu MySQL thật, lọc theo ngày và xuất CSV. Doanh thu chỉ tính đơn hoàn tất có thanh toán thành công; khoản hoàn tiền được loại ra.
- Quản lý tài khoản và nhân sự theo quyền ADMIN/HR_MANAGER; quản lý khuyến mãi theo ADMIN/PROMOTION_MANAGER. Chặn tự khóa, tự hạ quyền và thao tác làm mất quản trị viên đang hoạt động.
- Sửa validation checkout/tùy chọn món, luồng nhận tại quán, chuyển trạng thái thanh toán, lịch sử đơn và tạo giỏ hàng đồng thời.
- Đối chiếu class diagram: bổ sung quan hệ JPA giữa khách hàng/giỏ hàng, đơn/khuyến mãi/nhân viên, chi tiết/món và lịch sử/nhân viên. Giữ ID tương thích API và snapshot giá trong đơn.
- Tách kiểm thử trình duyệt ra `tests/frontend/`, kiểm tra HTTP ra `tests/runtime/`, preview ra `docs/previews/`; lưu diagram và mô tả kiến trúc trong `docs/architecture/`.
- Thêm launcher Tomcat và công cụ tạo nhân viên cục bộ trong profile Maven `dev`. Sửa chọn JDK trên PowerShell 5 và phát hiện cổng bị chiếm.

## Kiểm tra nền trước đợt rà soát ảnh

| Kiểm tra | Kết quả |
|---|---|
| Maven clean verify, Java 21, MySQL riêng | 384 kiểm thử đạt, không bỏ qua |
| Giao diện tài khoản/giỏ/checkout/đơn trên Chrome | 86/86 đạt |
| Biểu mẫu quản trị khách hàng/nhân viên/khuyến mãi | 3/3 đạt |
| Luồng đơn/thanh toán, biểu đồ/báo cáo, Esc và mở lại hộp thoại | 4/4 đạt |
| HTTP thật trên WAR, Tomcat 10.1 và MySQL | 90 request đạt |
| Tám trang admin thật trên Chrome, desktop/mobile | Không có lỗi JavaScript hoặc tràn ngang |
| Deploy và rollback qua môi trường giả lập | 19 kịch bản đạt |
| PowerShell chạy ứng dụng/tạo admin | Chạy được với JDK 21; tên tiếng Việt lưu đúng trong MySQL |
| Chạy launcher trên cổng đang bị chiếm | Thoát lỗi, không thông báo sẵn sàng sai |
| Đóng gói WAR | Không chứa test, preview, công cụ dev hoặc Tomcat nhúng |

HTTP kiểm tra đăng ký/đăng nhập, hồ sơ/địa chỉ, món/tùy chọn, voucher, giỏ hàng,
nhận tại quán/giao tận nơi, xác nhận/hoàn tiền, khóa tài khoản, các vai trò nhân viên
và toàn bộ trang admin. Database kiểm tra được tạo riêng; không reset database `crave` hiện có.

CI được bổ sung kiểm tra trình duyệt admin và chạy WAR qua HTTP trước khi upload artifact.
Các đoạn Bash và cú pháp đã kiểm tra cục bộ; workflow GitHub chưa chạy vì thay đổi chưa được push.

## Chạy lại

Xem [README](../../../README.md) để cấu hình DB, chạy `scripts/dev/run.ps1`
và tạo tài khoản bằng `scripts/dev/create-admin.ps1`.

## Rà soát 18 yêu cầu giao diện và PayOS — 2026-10-12

Đồng bộ `origin/main` tại `34c20b4`, tiếp tục nhánh `fix/admin-ui-project-review`.

| Yêu cầu | Kết quả |
|---|---|
| 1, 7 | Menu tài khoản chứa đăng xuất; các thao tác từng dòng nằm trong menu ba chấm. Menu đặt trên lớp nổi, hỗ trợ bàn phím/Escape và tránh bị bảng cắt. |
| 2 | Chuyển khoản PayOS: tạo link QR, đối chiếu API, webhook xác minh chữ ký, số tiền/mã đơn/mã link; retry dùng mã ổn định và tạo lại link đã hủy chưa nhận tiền. |
| 3, 6 | Bỏ viền focus kép cho input/select, dùng một border và hiệu ứng nhẹ; icon xóa SVG đủ tương phản. |
| 4 | Mũi tên select đặt cách mép phải 12px, có khoảng trống cho nội dung. |
| 5 | Icon thống kê lớn hơn, cùng kích thước trong ô nền. |
| 8 | Bỏ các nút lọc dư; chọn điều kiện tự lọc, tìm kiếm có debounce. Báo cáo tự cập nhật khi đổi ngày. |
| 9 | UTF-8 mặc định request/response/JSP, filter encoding chạy trước auth; kiểm tra source và HTML thực tế chống mojibake. |
| 10 | Phân trang danh sách quản trị, bảng báo cáo, menu khách (12/trang), đơn khách (8/trang) và ưu đãi (6/trang); giữ điều kiện lọc. |
| 11 | Ảnh món thật theo danh mục đổi mỗi 4,5 giây, preload và fallback ảnh; tắt chuyển ảnh khi giảm chuyển động. |
| 12 | Bỏ điều khiển thu gọn sidebar dư trên desktop. |
| 13, 14 | Món trong giỏ có nhãn và dấu kiểm; badge cập nhật ngay sau thêm/đổi/xóa/checkout và đồng bộ giữa tab. Chặn phản hồi GET cũ ghi đè thay đổi mới. |
| 15 | Giá và ký hiệu đồng dùng khoảng trắng không ngắt, `white-space: nowrap`, bố cục ưu đãi thích ứng màn hình hẹp. |
| 16 | Danh mục menu xuống dòng tự nhiên, bỏ vùng cuộn trong và thanh trắng; scrollbar khách hàng đồng màu nền. |
| 17 | Thống nhất Be Vietnam Pro hỗ trợ tiếng Việt, lưu WOFF2/OFL trong ứng dụng; tăng cỡ chữ admin và căn lại dòng/khoảng cách. |
| 18 | Commit đúng nhánh, PR tiếng Việt, tự merge theo yêu cầu người dùng sau CI. |

Số trong bảng theo thứ tự các mục người dùng gửi (các ảnh đánh số riêng);
mọi chỉnh sửa áp dụng trên component chung cho toàn ứng dụng.

Ảnh chạy thật từ database kiểm thử: [admin desktop](../../previews/admin-ui-review/admin-menu-desktop.png),
[admin mobile](../../previews/admin-ui-review/admin-overview-mobile.png),
[ảnh danh mục](../../previews/admin-ui-review/customer-categories-desktop.png),
[ưu đãi mobile](../../previews/admin-ui-review/customer-promotions-mobile.png).

## Kiểm chứng đợt rà soát

| Kiểm tra | Kết quả |
|---|---|
| Maven `-Pdev verify`, JDK 21, database review riêng | 414/414 đạt, không bỏ qua |
| Tài khoản, giỏ, checkout và chi tiết đơn trên Chrome | 86/86 đạt |
| Quản lý khách/nhân viên/ưu đãi | 4/4 đạt |
| Đơn/báo cáo/menu hành động/auto filter/phân trang quản trị | 7/7 đạt |
| Giỏ realtime, cross-tab, stale GET, ảnh danh mục, phân trang, callback PayOS | 14/14 đạt |
| WAR/Tomcat/MySQL qua HTTP | 91 request đạt, gồm từ khóa tiếng Việt và charset phản hồi |
| Tám trang quản trị desktop/mobile | Không tràn ngang hoặc lỗi JavaScript |
| Source UTF-8/JSP/font nội bộ | Đạt; CI chặn ký tự hỏng/mất encoding/thiếu font |
| Deploy/rollback và private Compose overlay | 22/22 tình huống đạt |
| Chuẩn bị production/migration/IMDSv2/webhook/không log secret | 16/16 đạt trên Windows; 1 bài symlink thật chạy trong CI Linux vì Windows thiếu quyền tạo symlink |
| API PayOS thật qua WAR đang chạy | Đơn chuyển khoản 2.000đ được lưu; tạo/dùng lại QR, chỉ chủ đơn được truy cập, đối chiếu pending, từ chối webhook giả, hủy và tạo lại link thành công; chưa chuyển tiền |

Kiểm thử PayOS bao gồm chủ sở hữu, từ chối payload sửa số tiền/khách hàng,
chữ ký sai, webhook trùng, callback giả `PAID`, thanh toán từng phần chỉ hoàn
tất khi API xác nhận tổng đã nhận đủ, và cạnh tranh khi tạo lại link đã hủy.
API trả 429 được chuyển thành thông báo chờ thử lại với `Retry-After`, không
mất đơn hoặc tự gọi lại liên tiếp; tạo lại link tận dụng snapshot đã kiểm chữ ký.
Giá trị khóa thật nằm trong `.env` bị Git bỏ qua và GitHub environment secrets.
Không có khóa thật trong WAR source, ảnh hay mô tả PR.

## Cấu hình triển khai

- Migration bổ sung tương thích WAR cũ: `database/migrations/2026-10-12-payos.sql`.
  CD chạy migration này bằng database đang cấu hình, không chạy bootstrap schema.
- Environment `production` đã có `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`.
  Variable `PAYOS_PUBLIC_BASE_URL` đã cấu hình `http://15.134.39.86:8080/crave`
  theo URL người dùng cung cấp; runner cũng hỗ trợ xác định địa chỉ EC2/cổng
  ứng dụng publish qua IMDSv2/Compose khi chưa đặt variable này.
- `.payos.env` và `.payos-compose.yaml` lưu ngoài checkout với quyền `0600`;
  deploy và rollback dùng cùng override, không thay cấu hình MySQL hay volume.
- Sau health check, CD gọi API đăng ký webhook công khai; callback không dựa vào session.
  Link/sync yêu cầu session khách hàng và kiểm tra chủ sở hữu đơn.
- Luồng PayOS dùng [API chính thức](https://payos.vn/docs/api/) và
  [HMAC webhook](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/).

Chưa thực hiện chuyển tiền thật. Theo quy tắc thanh toán có sẵn, đơn đã bị cửa hàng
hủy cần đối soát thủ công nếu khách chuyển tiền sau thời điểm hủy.

## Kiểm tra sau merge và triển khai

PR [#34](https://github.com/nguyenvinhz/Servlet_Project_Crave/pull/34) đã merge vào
`main` tại `797875d`. CI Linux đạt 414/414 backend, 111/111 trình duyệt,
91 request HTTP, 22 tình huống deploy/rollback và 17 bài chuẩn bị production.
WAR triển khai thành công; 9 kiểm tra health/asset hash/UTF-8/webhook công khai
đều đạt. Chrome production xác nhận menu/ưu đãi desktop và mobile dùng đúng font,
6 ảnh danh mục tải được và tự đổi, không tràn ngang, ký tự hỏng hoặc scrollbar trắng.

Bước đăng ký webhook ban đầu bị HTTP 403 với header mặc định của Python `urllib`.
Cùng khóa và URL, Node trả HTTP 200/code `00`; request Python với
`User-Agent: Crave-Deployment/1.0` và `Accept: application/json` cũng thành công.
Đã đăng ký webhook thật và bổ sung hai header vào CD; lỗi HTTP chỉ ghi status,
không ghi response body hoặc khóa. Thay đổi này được commit/PR tiếp trên
`fix/admin-ui-project-review` trước khi tự merge theo yêu cầu người dùng.
