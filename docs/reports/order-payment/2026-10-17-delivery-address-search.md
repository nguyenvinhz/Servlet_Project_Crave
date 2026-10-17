# Sửa tìm địa chỉ và chọn điểm giao hàng — 2026-10-17

Ghi chú: báo cáo này ghi giai đoạn xử lý với Nominatim XML. Theo xác nhận sau
đó của người dùng về ngoại lệ dịch vụ ngoài, Map đã được khôi phục Photon
GeoJSON và ảnh nền OSM; xem [báo cáo khôi phục mới](2026-10-17-native-map-restoration.md).

## Hiện tượng và nguyên nhân

Người dùng nhập địa chỉ nhưng ứng dụng không xác định được vị trí. Kiểm tra
local thấy tìm tên đường trả HTTP 400, trong khi tìm bằng tọa độ trả đúng kết quả.
DNS mặc định của máy trả NXDOMAIN cho `nominatim.openstreetmap.org` và
`tile.openstreetmap.org`. Truy vấn cùng tên miền qua DNS `1.1.1.1` trả IP;
HTTPS với IP này, giữ nguyên hostname/TLS, trả HTTP 200 và các vị trí đường
Võ Văn Ngân ở khu vực Thủ Đức. Xóa cache DNS tạm thời chưa khắc phục được DNS mặc định.

Luồng cũ còn che thông báo của dịch vụ bằng lỗi chung. Khi sửa ô địa chỉ,
tọa độ bị xóa nhưng ghim và nút xác nhận vẫn hiện; bấm nút không có phản hồi.

## Đã thay đổi

- Geocoder XML tìm quanh cửa hàng, ưu tiên kết quả gần và nhãn tiếng Việt.
  Không trả đường trùng tên ở tỉnh xa làm gợi ý gần cửa hàng.
- Nếu thiếu dữ liệu số nhà, thử tên đường nhưng giữ phần phường/thành phố,
  ghi rõ chưa xác định số nhà. Không cắt ngày trong tên đường như `3 Tháng 2`.
  Khách vẫn phải kiểm tra ghim trước khi xác nhận.
- Bỏ riêng candidate XML lỗi; lưu kết quả rỗng hai phút để giảm yêu cầu lặp.
- Servlet trả thông báo lỗi bằng JSP HTML có escape, giữ đúng HTTP status.
  Trình duyệt phân biệt không có kết quả và không kết nối được dịch vụ.
- Enter trong ô địa chỉ thực hiện tìm kiếm. Chọn gợi ý giữ số nhà/địa chỉ
  khách đã nhập và hiển thị địa điểm khớp riêng.
- Sửa địa chỉ xóa ghim/tọa độ/báo phí cũ. Yêu cầu cũ bị hủy; kết quả tìm kiếm,
  báo phí hoặc vị trí GPS đến muộn không ghi đè lựa chọn mới. Có timeout chờ.
- Thêm kiểm thử trình duyệt bản đồ vào CI và lưu ảnh khi kiểm tra.
- Bổ sung DNS dự phòng chỉ trong HTTP client của geocoder: ưu tiên DNS hệ thống,
  chỉ tra `nominatim.openstreetmap.org` qua JNDI/DNS `1.1.1.1` khi hệ thống báo
  không tìm thấy hostname. Không sửa DNS Wi-Fi, hosts hoặc cấu hình JVM toàn cục.
- Dùng Apache HttpClient5 để gắn resolver cho riêng kết nối này, giữ hostname
  HTTPS và xác thực TLS mặc định, không dùng IP dịch vụ cố định. Servlet đóng
  client khi dừng. Đồng bộ SLF4J API/provider 2.0.16 để giữ log hoạt động.

Giữ nguyên CSS, font, bố cục, Leaflet và hình ảnh. Giao tiếp ứng dụng vẫn là
form Servlet/JSP, kết quả bản đồ là HTML/XML, không thêm JSON hay Spring.
Dependency HTTP thêm vào là `org.apache.httpcomponents.client5:httpclient5:5.6.4`.

## Kiểm tra

- Maven `-Pdev verify`: 485 test, không failure/error/skip; build WAR thành công.
- Sau khi đồng bộ SLF4J: 35 test DNS/HTTP/geocoder/Servlet đạt, đóng gói lại WAR.
  Full suite dùng database local cũ `crave`, giữ dữ liệu `crave_live_local` riêng.
- Chrome: 9 nhóm regression tìm địa chỉ/ghim đều đạt với JS/JSP/Leaflet thực
  và fixture form/HTML local; không phụ thuộc database, SMTP hoặc geocoder ngoài.
- WAR chạy local: health/application/database UP, checkout JSP đúng phiên bản,
  tìm tọa độ, báo phí và lỗi HTML fragment đều hoạt động.
- Workflow qua actionlint; JavaScript và `git diff --check` đạt.

Người dùng đã chọn **giữ nguyên DNS, chỉ sửa code**; không thay cấu hình DNS
Wi-Fi. Với resolver riêng trong ứng dụng, HTTPS thật đã trả HTTP 200. Tìm
`1 Võ Văn Ngân, Thủ Đức` qua Servlet trả hai gợi ý, có đúng tọa độ trường đại học
`10.8527907, 106.7725584`; báo phí từ điểm này trả HTTP 200 và đủ điều kiện giao.
Trình duyệt thật cũng chọn được gợi ý, giữ số nhà và xác nhận phí 7.000đ;
không có lỗi JavaScript hoặc gửi đơn hàng trong lượt kiểm tra.
Kiểm chứng HTTP dùng ứng dụng tạm cổng 8082 và tài khoản demo ở database cũ,
không sửa tài khoản hoặc dữ liệu của dump. Bản WAR mới chạy cổng 8081 với
`crave_live_local` được nạp từ `seed_live.sql`.
Máy chủ kiểm chứng 8082 đã dừng sau khi kiểm tra; cổng 8081 tiếp tục chạy.

Resolver dự phòng chỉ phục vụ tìm địa chỉ. Nền ảnh Leaflet vẫn dùng URL OSM
hiện tại và kết nối mạng của trình duyệt. Khi dịch vụ thật hoặc mạng không
truy cập được cả DNS dự phòng/HTTPS, ứng dụng vẫn báo lỗi rõ ràng.
Bản sao DNS dùng cho chẩn đoán nằm trong `.runtime/map-audit/dns-before*`.

Tham khảo: [API tìm kiếm Nominatim](https://nominatim.org/release-docs/latest/api/Search/),
[Apache DNS resolver](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/DnsResolver.html),
[JDK DNS/JNDI](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.naming.dns/module-summary.html),
[hướng dẫn bản đồ và phí giao hàng](../../architecture/delivery-and-cart-editing.md).
