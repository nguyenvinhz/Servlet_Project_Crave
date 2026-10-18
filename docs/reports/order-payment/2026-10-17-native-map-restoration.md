# Khôi phục Map và phạm vi tích hợp dịch vụ ngoài — 2026-10-17

## Yêu cầu áp dụng

Theo xác nhận mới của người dùng, SMTP, payOS, CI/CD và Map dùng giao thức,
JSON/SDK hoặc định dạng mà nhà cung cấp yêu cầu. Nghiệp vụ còn lại tiếp tục
dùng Servlet/JSP/JPA/Hikari đơn giản, không Spring và không lớp mapper riêng.
Giữ giao diện ban đầu của project, DNS máy và database local đang dùng.

Đợt chuyển Map sang Nominatim XML trước đó đã áp dụng giới hạn JSON quá rộng.
Ngoài lỗi DNS ảnh nền, luồng tìm thủ công làm mất một số hành vi của bản cũ.

## Đã khôi phục

- `AddressGeocoder` dùng Photon GeoJSON theo API của Photon, gồm tìm kiếm,
  reverse, thứ tự tọa độ kinh độ/vĩ độ, country/bbox/focus và xử lý địa chỉ Việt Nam.
  Jackson nằm trong gateway dịch vụ Map, không thêm mapper nghiệp vụ.
- Tìm tự động sau khi ngừng nhập 1,2 giây; chọn địa chỉ đã lưu/mặc định tự định vị.
  Một kết quả tự ghim, nhiều kết quả có lựa chọn và thu/phóng bản đồ.
- Khôi phục tooltip, nhãn và ghi chú địa chỉ, cách hiển thị phí/tổng tiền gọn.
  Giữ số nhà khách nhập, xác nhận vị trí gợi ý và bảo vệ kết quả đến muộn.
- `MapTileServlet` trả đúng PNG OpenStreetMap gốc để tránh lỗi DNS ảnh ở browser.
  Chỉ nhận z/x/y hợp lệ, yêu cầu tài khoản khách hàng; không nhận URL tùy ý.
  Chuyển tiếp Referer/điều kiện cache, dùng User-Agent xác định ứng dụng và
  giữ attribution. Không chuyển cookie hay token đăng nhập tới nhà cung cấp.
- Module HTTP cache chính thức của Apache xử lý Cache-Control/Expires/ETag
  và conditional revalidation; cache giới hạn 128 mục, không tải trước/offline.
- DNS dự phòng chỉ trong HTTP client Map, HTTPS vẫn kiểm tra hostname/chứng chỉ
  bình thường. Không thay DNS Wi-Fi, hosts hoặc cấu hình DNS JVM toàn cục.
- Cập nhật URL Photon mặc định trong env mẫu, Compose và workflow deploy;
  tài liệu ghi đúng ngoại lệ của các tích hợp ngoài. SMTP/payOS/CI/CD đang dùng
  đúng giao thức nên giữ luồng hiện có; tài liệu payOS bỏ IP AWS cố định đã cũ.

Không đổi CSS, font, hình ảnh hoặc bố cục của project. Core vẫn nhận form và
render JSP. Không nạp lại database, đổi tài khoản hoặc gửi đơn thử trong lượt
kiểm chứng live; ứng dụng chính vẫn dùng `crave_live_local` từ `seed_live.sql`.

## Kết quả kiểm tra

- Maven `-Pdev verify`: **553 test**, không failure/error/skip, WAR build thành công.
  Các test tích hợp dùng database local cũ `crave`, tách khỏi database đang xem.
- **13 tình huống browser** cho debounce, địa chỉ mặc định, ghim, chọn kết quả,
  quote, request đến muộn, vị trí GPS và giữ số nhà đều đạt.
- Live Photon tìm `1 Võ Văn Ngân, Thủ Đức`, chọn đúng trường tại
  `10.8527907, 106.7725584`; xác nhận phí **7.000đ**.
- Live ảnh nền: 6/6 tile desktop và 4/4 tile mobile tải được PNG 256×256,
  HTTP 200; không có lỗi tile hoặc exception JavaScript, không submit đơn.
- Cache test chứng minh tái sử dụng ảnh và revalidate bằng ETag/304,
  không tải lại upstream khi còn hiệu lực. Test Servlet kiểm tra phân quyền,
  giới hạn đường dẫn, dữ liệu PNG, lỗi upstream và đóng kết nối.
- Actionlint và Compose config đạt; Compose dùng Photon đúng mặc định mới.
- Local cổng **8081** tiếp tục chạy với health/application/database UP.
  Máy chủ kiểm chứng cổng 8082 được dừng sau khi kiểm tra.

Bằng chứng ảnh/kết quả nằm trong `.runtime/map-audit/`: `live-search-quoted.png`,
`live-search-mobile.png`, `live-servlet-search-result.json`.

Nguồn giao thức: [Photon API](https://github.com/komoot/photon/blob/master/docs/api-v1.md),
[Photon demo usage](https://github.com/komoot/photon#demo-server),
[OSM tile policy](https://operations.osmfoundation.org/policies/tiles/),
[Apache HTTP cache](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5-cache/apidocs/org/apache/hc/client5/http/impl/cache/CachingHttpClientBuilder.html).
