# Báo cáo đơn giản hóa backend Servlet/JSP

Ngày hoàn tất: **2026-10-17**. Thực hiện theo yêu cầu dùng Servlet cơ bản, JSP,
JPA và HikariCP, không dùng mapper hoặc JSON cho nghiệp vụ thông thường.
Người dùng đã đồng ý thay đổi luồng JS/JSP để bỏ JSON và giữ nguyên hình thức giao diện.

## Thay đổi đã thực hiện

- Gom các Servlet vào `com.foodordering.servlet`; bỏ thư mục `api/` và
  `servlet/page/`. Đặt tên Servlet trực tiếp theo chức năng.
- Thay 15 file repository/interface/implementation bằng 9 lớp DAO JPA cụ thể
  trong `dao/`: Account, Category, Food, FoodOption, Cart, CartItem,
  CustomerOrder, Payment và Promotion.
- Gộp các cặp service interface/implementation đơn hàng và thanh toán thành
  `CustomerOrderService` và `PaymentService` cụ thể.
- Xóa 5 lớp mapper. Service chuẩn bị dữ liệu form/JSP bằng Java thông thường,
  giữ các bean hiển thị và quy tắc nghiệp vụ đang dùng.
- Chuyển đăng nhập, OTP, hồ sơ, địa chỉ, món, tùy chọn, giỏ hàng, voucher,
  checkout, đặt/hủy đơn và quản trị sang form → Servlet → service/DAO → JSP.
  Servlet đọc tham số form; request/session attributes đưa dữ liệu sang JSP;
  redirect sau khi lưu.
- Bỏ `JsonUtils`, `JsonProvider`, `ApiResponse`, `ApiError` và JSON DTO
  annotations. Jackson chỉ dùng trong `PayosClient`, `PayosJson`,
  nghiệp vụ payOS và webhook có chữ ký.
- Trang QR nhận HTML JSP từ `/payos/status`, ảnh PNG từ `/payos/qr` và form
  tạo link từ `/payos/create`. Webhook `/api/payos/webhook` giữ JSON đúng giao thức.
- Đổi tra địa chỉ sang Nominatim XML bằng thư viện XML của JDK.
  Các Servlet giao hàng trả đoạn HTML JSP để giữ bản đồ và báo phí tại chỗ.
  `CRAVE_GEOCODER_URL` tùy chỉnh phải trỏ tới endpoint Nominatim XML;
  cấu hình Photon JSON cũ cần đổi theo `.env.example`.
- Health trả văn bản `application=UP` / `database=UP`; giữ alias
  `/api/health`. Script triển khai đọc cả định dạng mới và JSON phiên bản cũ
  để rollback vẫn hoạt động.
- Công cụ tạo quản trị viên nhận từng đối số UTF-8/base64 trên Windows,
  không dùng JSON và không đưa mật khẩu lên command line.

## Giao diện và xử lý lỗi

CSS, font, ảnh và vendor assets không thay đổi. JSP/JS giữ kiểu hiển thị và
các thành phần hiện có, chuyển cách lấy dữ liệu và gửi thao tác sang form/HTML.
Đã xem ảnh chụp menu, hồ sơ, giỏ hàng, checkout, sổ địa chỉ mobile và quản trị.

Sửa lỗi mã hóa tiếng Việt trong fragment nhãn đơn hàng; thông báo checkout và
đơn hàng hiển thị đúng CSS hiện có. Form hết phiên quay về trang GET phù hợp sau
đăng nhập, tránh chuyển tới URL chỉ nhận POST. Đăng nhập tài khoản khác xóa
voucher và số món của phiên trước. Giữ validation, phân quyền, đổi session ID,
kiểm tra nguồn gửi form và quyền sở hữu; không đưa lỗi JDBC nội bộ ra giao diện.

Entity, schema, transaction đặt hàng, snapshot giá, kiểm tra voucher và pool
HikariCP được giữ nguyên. Không thêm Spring hoặc framework backend khác.

## Kiểm tra đã chạy

| Kiểm tra | Kết quả |
|---|---|
| Maven `-Pdev verify`, JDK 21 | **465 test đạt**, 0 lỗi, 0 bỏ qua; có integration MySQL thật |
| `CartPromotionServiceTest` với `java -ea` | **9 kiểm tra đạt** |
| Browser smoke trên WAR/Tomcat thật | **62 kiểm tra đạt** |
| HTTP smoke dùng form | **31 kiểm tra đạt** |
| Deploy/rollback fixtures | **72 tình huống đạt** |
| Đăng ký webhook payOS | **12 test đạt** |
| Jasper biên dịch toàn bộ JSP | **0 lỗi** |
| JavaScript frontend và smoke scripts | Cú pháp đạt |
| YAML CI và shell bước runtime | Đọc/kiểm tra cú pháp đạt; health LF/CRLF và DOWN đã kiểm tra |
| Cấu trúc WAR | Không chứa API/mapper/repository/Impl cũ hoặc launcher/Tomcat phát triển |
| Git diff | Không lỗi khoảng trắng; CSS/font/ảnh/vendor giữ nguyên |

Browser smoke kiểm tra tài khoản/SMTP OTP cục bộ, reset/change mật khẩu, hồ sơ
tiếng Việt, địa chỉ CRUD/mặc định/XSS/mobile, tìm món, tùy chọn, sửa giỏ,
voucher sai, checkout tiền mặt, đơn/hủy đơn, tám trang quản trị, CRUD danh mục
và khóa/mở khách hàng. Không có request JSON nghiệp vụ, API cũ hoặc lỗi JavaScript.
PayOS được kiểm tra bằng fixture có chữ ký và giải mã ảnh QR; không chuyển tiền thật.

Các test cũ mock JSON đã được thay bằng test form và browser chạy ứng dụng thật.
CI đã chuyển sang kiểm tra form/HTML và health văn bản.
Tài khoản, đơn và danh mục test của browser đã được dọn; Tomcat/SMTP riêng cho
kiểm thử đã dừng. MySQL cục bộ dùng dữ liệu có sẵn được khởi động ở cổng 3306.

## Tệp bàn giao

- WAR: `backend/target/crave.war`.
- Hướng dẫn chạy: [README dự án](../../../README.md).
- [Hợp đồng form Servlet](../../architecture/servlet-forms.md).
- [Cấu trúc mã nguồn](../../architecture/README.md).
- [Giao hàng và chỉnh sửa giỏ hàng](../../architecture/delivery-and-cart-editing.md).
- Browser smoke: `tests/frontend/servlet-form-browser-smoke.cjs`.
- Log kiểm thử: `backend/target/servlet-final-verify.log`,
  `backend/target/cart-promotion-legacy-check.log`; ảnh/log browser trong
  `.runtime/form-browser/` (không đưa vào WAR/Git).

Ghi chú môi trường: lần `mvn clean` trước khi hoàn tất gặp thư mục Tomcat cũ
trong `backend/target/`. Các file gốc bị thiếu đã được khôi phục có kiểm tra hash,
giữ nguyên các file đã tồn tại. Bản WAR bàn giao được build từ thư mục output
đã làm sạch có chọn lọc và được chạy kiểm thử trong Tomcat riêng; không khởi chạy
thư mục Tomcat cũ chứa các class từ nhiều phiên bản.