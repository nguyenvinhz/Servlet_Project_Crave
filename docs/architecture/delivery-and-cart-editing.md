# Giao hàng và chỉnh sửa giỏ hàng

Checkout và giỏ hàng dùng form, Servlet, JPA và JSP. CSS, Leaflet, hình ảnh và
các lớp giao diện cũ được giữ lại. Không gửi JSON giữa trình duyệt và backend.

## Phí giao hàng

`DeliveryFeeService` tính khoảng cách Haversine từ cửa hàng, dùng khoảng cách
chưa làm tròn để chọn mức phí và giới hạn khu vực; phí cuối làm tròn HALF_UP
đến đơn vị đồng. Đây là khoảng cách địa lý, không phải tuyến xe máy.

| Khoảng cách | Phí |
|---|---|
| Dưới 1 km | 0 đ |
| Từ 1 đến dưới 3 km | 4.000 đ |
| Từ 3 đến dưới 5 km | 7.000 đ |
| Từ 5 đến dưới 7 km | 7.000 + (km − 5) × 2.500 đ |
| Từ 7 đến 10 km | 12.000 + (km − 7) × 3.000 đ |
| Trên 10 km | Không hỗ trợ giao |
| Lấy tại quán | 0 đ |

Subtotal tiền món lớn hơn 150.000 đ được miễn phí giao trong khu vực hỗ trợ,
tính trước khi giảm voucher. Đúng 150.000 đ vẫn tính phí bình thường.
`CustomerOrderService` đọc giỏ hàng đã khóa và tính lại phí khi tạo đơn;
không nhận số tiền hay kết quả đủ điều kiện từ trình duyệt.

Cấu hình `CRAVE_STORE_ADDRESS`, `CRAVE_STORE_LATITUDE`,
`CRAVE_STORE_LONGITUDE` để đặt đúng vị trí cửa hàng. Bảng phí và bán kính ở
đây là tài liệu kỹ thuật; trang khách chỉ hiển thị phí cuối và thông báo hỗ trợ.

## Địa chỉ và bản đồ

JSP checkout render sẵn hồ sơ, sổ địa chỉ, subtotal, voucher và vị trí cửa hàng.
Địa chỉ mặc định hoặc địa chỉ đã lưu được tìm khi chọn. Địa chỉ đang nhập được
tìm sau khi ngừng gõ 1,2 giây; khách cũng có thể bấm tìm hoặc nhấn Enter.
Một kết quả tự đặt ghim, nhiều kết quả hiện lựa chọn và thu/phóng bản đồ để xem.
Vị trí gợi ý cần khách kiểm tra ghim rồi bấm “Xác nhận điểm nhận”.
Đổi địa chỉ, di chuyển ghim hoặc đổi hình thức nhận sẽ hủy báo giá cũ.
Server nhận cùng tọa độ đã chọn để báo phí và tạo đơn.
Khi sửa địa chỉ, ghim và tọa độ cũ được xóa; yêu cầu tìm kiếm/báo phí đang chờ
bị hủy để kết quả cũ không ghi đè lựa chọn mới. Chọn gợi ý vẫn giữ số nhà và
địa chỉ khách nhập trong thông tin nhận hàng; tên địa điểm khớp hiện riêng để kiểm tra.

`AddressGeocoder` dùng Photon theo [API GeoJSON chính thức](https://github.com/komoot/photon/blob/master/docs/api-v1.md):
`/api/` cho tìm kiếm và `/reverse` cho tra ngược. `CRAVE_GEOCODER_URL` mặc định
là `https://photon.komoot.io/api/`; URL tùy chỉnh phải hỗ trợ cùng giao thức.
JSON được xử lý trong gateway tích hợp Map, không tạo lớp mapper nghiệp vụ.

Tìm tên đường giới hạn trong hộp ±0,2 độ quanh cửa hàng và ưu tiên điểm gần cửa hàng,
tránh đường trùng tên ở tỉnh khác. Hộp tìm kiếm rộng hơn bán kính giao hàng 10 km;
phí và điều kiện giao hàng vẫn được kiểm tra riêng. Dịch vụ trả nhãn tiếng Việt.
Nếu địa chỉ có số nhà không có kết quả, thử lại tên đường và giữ nguyên phần
phường/thành phố. Kết quả này ghi rõ “chưa xác định số nhà”; khách cần kéo ghim
đến đúng cửa nhận hàng và xác nhận, hệ thống không tự coi tâm đường là số nhà.

Có cache tối đa 128 mục trong sáu giờ (kết quả rỗng hai phút), timeout kết nối/đọc và giới hạn một yêu cầu
tra địa chỉ mỗi 1,1 giây trong một ứng dụng. Photon hỗ trợ tìm trong lúc nhập;
debounce và cache giảm số yêu cầu. GeoJSON nhận tọa độ theo thứ tự kinh độ,
vĩ độ; backend kiểm tra kiểu dữ liệu, giới hạn kích thước phản hồi và vùng Việt Nam.
Tra ngược lấy tên gần ghim nhưng luôn giữ đúng tọa độ khách chọn; kết quả xa hơn
75 m hoặc lỗi mạng dùng nhãn tọa độ thay thế.

`CoordinateParser` nhận tọa độ thập phân và nhãn tọa độ trong ngoặc;
tọa độ hợp lệ bỏ qua dịch vụ tìm kiếm. Leaflet và giấy phép được lưu tại
`frontend/assets/vendor/leaflet/`. Ảnh nền vẫn là ảnh OpenStreetMap gốc, không
đổi kiểu bản đồ, CSS, font hoặc kích thước. Browser tải PNG qua `MapTileServlet`
để DNS của browser không làm nền bản đồ xám; upstream vẫn dùng URL chuẩn
`https://tile.openstreetmap.org/{z}/{x}/{y}.png`.

Servlet ảnh yêu cầu tài khoản khách hàng, kiểm tra z/x/y và chuyển tiếp Referer
thật, User-Agent của ứng dụng cùng điều kiện cache. Apache HTTP cache xử lý
Cache-Control/Expires/ETag và revalidation theo [chính sách OSM](https://operations.osmfoundation.org/policies/tiles/).
Cache giới hạn 128 mục; không tải trước vùng bản đồ hoặc cung cấp tải offline.

| Servlet | Tham số form | Kết quả |
|---|---|---|
| `POST /delivery/search` | `address` | Các nút HTML kết quả địa chỉ |
| `POST /delivery/reverse` | `latitude`, `longitude` | HTML mô tả gần điểm ghim |
| `POST /delivery/quote` | `latitude`, `longitude` | HTML phí và thông báo |
| `GET /map/tiles/{z}/{x}/{y}.png` | Chỉ z/x/y hợp lệ | PNG gốc và header cache |
| `POST /orders/create` | Các trường checkout | Lưu đơn trong transaction rồi redirect |

Lỗi của các Servlet vị trí trả HTML `data-delivery-error` cùng HTTP status tương ứng;
JS hiển thị thông báo này để phân biệt không tìm thấy địa chỉ với không kết nối
được dịch vụ. JSP escape nội dung bằng `c:out`. Yêu cầu trình duyệt có timeout 15 giây.

Nếu nhập tọa độ tìm được nhưng tên đường luôn báo không kết nối, kiểm tra DNS
và HTTPS của `photon.komoot.io`; nền bản đồ dùng `tile.openstreetmap.org`.
Ví dụ Windows: `Resolve-DnsName photon.komoot.io` và đối chiếu
`Resolve-DnsName photon.komoot.io -Server 1.1.1.1`.

HTTP client thử DNS hệ thống trước. Khi không tìm thấy một hostname dịch vụ Map
trong allowlist cố định, `MapDnsResolver` mới tra bản ghi A qua JNDI tới
`1.1.1.1`, với thời gian chờ DNS ngắn. Cơ chế này chỉ áp dụng trong HTTP client
Map; không thay DNS Wi-Fi, hosts hay thiết lập DNS JVM toàn cục.
Endpoint tùy chỉnh và các hostname khác vẫn dùng DNS hệ thống, không tự gửi
tên miền nội bộ sang DNS công cộng.

Apache HttpClient5 giữ URI/hostname HTTPS gốc và xác thực TLS mặc định. Không
lưu IP dịch vụ cố định, không tắt xác thực chứng chỉ. Giữ định dạng dịch vụ yêu cầu:
Photon GeoJSON và tile PNG. Client giới hạn kích thước phản hồi, tắt redirect/retry tự động và được
đóng khi Servlet dừng. Nếu cả DNS dự phòng hoặc HTTPS đều không kết nối được,
ứng dụng vẫn hiển thị thông báo lỗi, không tự tạo kết quả địa chỉ.

## Sửa món trong giỏ

`GET /cart?edit=ID` tải chi tiết từ server và mở dialog hiện tại.
Form `POST /cart/action` nhận `action=update`, `cartItemId`, `quantity`,
`note`, `updateOptions=true` và các `optionIds`. Không có cờ thay tùy chọn
thì giữ nguyên lựa chọn; có cờ mà không có option thì xóa các lựa chọn.

Service kiểm tra quyền sở hữu, trạng thái món/option, option thuộc đúng món,
trùng lặp và nhóm chỉ cho chọn một. Khóa giỏ trước mục giỏ để đồng bộ với
checkout; quantity, note và option cập nhật trong một transaction.
Giá hiện hành và voucher được tính lại ở server sau khi lưu.

Kiểm thử tập trung ở `AddressGeocoderTest`, `DeliveryServletTest`,
`DeliveryFeeServiceTest`, `CartCustomizationServiceTest`,
`CartPromotionIntegrationTest`, `CartCheckoutIntegrationTest` và browser smoke
form thật. `node tests/frontend/delivery-map-browser-smoke.cjs` chạy riêng các
tình huống tìm địa chỉ, kết quả đến muộn và đồng bộ ghim bằng Chrome/Edge với
fixture form/HTML local, không cần database hay dịch vụ bản đồ ngoài mạng.
