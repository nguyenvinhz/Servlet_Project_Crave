# Phân công nhiệm vụ phát triển trong 3 ngày

## 1. Mục tiêu

Trong 3 ngày, nhóm xây dựng luồng đặt đồ ăn chạy trên Java Servlet, JSP, JPA/JDBC, MySQL và Tomcat. Mỗi thành viên chịu trách nhiệm một vertical slice và phải trực tiếp code đủ ba phần:

- Backend: entity, repository, service, servlet và nghiệp vụ thuộc phạm vi được giao.
- Frontend: JSP, HTML/CSS/JavaScript và các trạng thái giao diện tương ứng.
- API: endpoint JSON phục vụ AJAX/fetch và tích hợp giữa các màn hình.

## 2. Quy tắc phụ thuộc bắt buộc

Ngày 1 → Gate 1 đạt 100% → Ngày 2 → Gate 2 đạt 100% → Ngày 3 → Hoàn thành

- Không thành viên nào bắt đầu nhiệm vụ Ngày 2 khi Gate 1 chưa được cả nhóm xác nhận.
- Không thành viên nào bắt đầu nhiệm vụ Ngày 3 khi Gate 2 chưa được cả nhóm xác nhận.
- Nếu một phần việc làm Gate bị chặn, cả nhóm ưu tiên hỗ trợ xử lý thay vì tự chuyển sang ngày tiếp theo.
- Mọi thay đổi ở file dùng chung phải được thông báo trước để tránh ghi đè code của nhau.

## 3. Phạm vi phụ trách chính

| Thành viên | Vertical slice chính | Backend | Frontend | API |
|---|---|---|---|---|
| Nguyễn Quang Vinh | Tài khoản, xác thực, hồ sơ, địa chỉ | User, Customer, Address, filter xác thực | Login, register, profile, address | Auth, profile, address API |
| Nguyễn Minh Huân | Menu và quản lý món | Category, Food, Food Option | Menu, product detail, admin menu/category/option | Menu/catalog và admin menu API |
| Ung Văn Trí | Giỏ hàng và khuyến mãi | Cart, Cart Item, Promotion | Cart, promotion, voucher tại checkout | Cart và promotion validation API |
| Nguyễn Đức Phát | Đơn hàng và thanh toán | Order, Order Detail, Status History, Payment | Checkout, orders, admin orders/payments | Order, order status và payment API |

Phạm vi chính giúp tránh xung đột file, nhưng mọi thành viên vẫn phải phối hợp ở các điểm tích hợp liên domain.

## 4. Ngày 1 — Dựng nền và chốt hợp đồng

### Nguyễn Quang Vinh

- Backend: thiết lập Maven/Tomcat, cấu hình JPA/connection pool ở mức chạy được; tạo nền cho User, Customer và Address.
- Frontend: dựng khung login, register, profile và address; chuẩn bị form field và vùng hiển thị lỗi.
- API: chốt request/response cho đăng nhập, đăng ký, profile và địa chỉ; tạo route nền.
- Đầu ra: ứng dụng khởi động được, kết nối database được và các route thuộc phạm vi có thể được gọi.

### Nguyễn Minh Huân

- Backend: tạo nền cho Category, Food và Food Option; xác định quan hệ và repository contract.
- Frontend: dựng component cần thiết cho menu, danh sách món, chi tiết món và khung admin menu.
- API: chốt response model cho danh mục, danh sách món, chi tiết món và tùy chọn món.
- Đầu ra: dữ liệu menu mẫu có thể đọc qua service/API và hiển thị ở JSP khung.

### Ung Văn Trí

- Backend: tạo nền cho Cart, Cart Item và Promotion; xác định contract tính tạm tính và kiểm tra voucher.
- Frontend: dựng trang cart, promotion và khu vực nhập voucher; chuẩn bị trạng thái rỗng, lỗi và loading.
- API: thống nhất định dạng JSON thành công/lỗi; chốt contract thêm, sửa, xóa cart item và kiểm tra voucher.
- Đầu ra: route cart/promotion tồn tại và tuân thủ định dạng JSON chung.

### Nguyễn Đức Phát

- Backend: tạo nền cho Order, Order Detail, Order Status History và Payment; chốt ranh giới transaction đặt hàng.
- Frontend: dựng checkout, danh sách/chi tiết đơn và khung admin xử lý đơn, thanh toán.
- API: chốt contract tạo đơn, xem đơn, cập nhật trạng thái đơn và thanh toán.
- Đầu ra: schema/domain order-payment thống nhất và route nền có thể được gọi.

### Gate 1 — Phải đạt trước khi sang Ngày 2

- [ ] Maven build thành công và ứng dụng deploy được lên Tomcat.
- [ ] Kết nối MySQL qua pool/JPA hoạt động; không commit credential thật.
- [ ] Domain model và API contract của cả bốn vertical slice đã được review.
- [ ] Các JSP khung render được, không có lỗi server.
- [ ] API dùng chung định dạng JSON và mã HTTP đã thống nhất.
- [ ] Code Ngày 1 đã merge, không còn conflict hoặc lỗi build.

## 5. Ngày 2 — Hoàn thiện chức năng theo vertical slice

### Nguyễn Quang Vinh

- Backend: hoàn thiện đăng ký, đăng nhập/đăng xuất, session, filter xác thực, profile và CRUD địa chỉ.
- Frontend: kết nối form auth/profile/address với Servlet; validation client và hiển thị lỗi server.
- API: hoàn thiện auth/profile/address API và kiểm tra quyền truy cập.
- Demo: đăng ký → đăng nhập → sửa profile → thêm/chọn địa chỉ.

### Nguyễn Minh Huân

- Backend: hoàn thiện đọc menu, lọc danh mục, chi tiết món/tùy chọn và CRUD quản trị menu.
- Frontend: kết nối menu/product detail và admin menu/category/option với dữ liệu thật.
- API: hoàn thiện catalog API, admin menu API và validation món/tùy chọn.
- Demo: xem menu → lọc danh mục → xem món/tùy chọn → admin cập nhật món.

### Ung Văn Trí

- Backend: hoàn thiện thêm/sửa/xóa cart item, tính tổng, xử lý tùy chọn món và validation khuyến mãi.
- Frontend: kết nối cart và voucher bằng form/AJAX; cập nhật số lượng, tổng tiền và lỗi.
- API: hoàn thiện cart API và promotion validation API; không tin giá/tổng tiền từ client.
- Demo: thêm món → đổi số lượng/tùy chọn → áp voucher → tính lại tổng.

### Nguyễn Đức Phát

- Backend: hoàn thiện tạo đơn, chi tiết đơn, lịch sử trạng thái, thanh toán và cập nhật trạng thái bởi nhân viên.
- Frontend: kết nối checkout, lịch sử/chi tiết đơn và admin orders/payments với dữ liệu thật.
- API: hoàn thiện order/payment API và admin order status API; bảo vệ cập nhật theo quyền.
- Demo: checkout → tạo đơn → thanh toán → admin cập nhật trạng thái → khách xem lại.

### Gate 2 — Phải đạt trước khi sang Ngày 3

- [ ] Bốn demo bắt buộc của Ngày 2 chạy được với MySQL.
- [ ] Servlet/JSP và API đều gọi service; không đặt business logic trong JSP hoặc JavaScript.
- [ ] API kiểm tra authentication/authorization ở endpoint cần bảo vệ.
- [ ] Không lấy giá, giảm giá hoặc tổng tiền do client gửi lên làm nguồn tin cậy.
- [ ] Luồng lỗi có thông báo rõ trên frontend và JSON phù hợp ở API.
- [ ] Code Ngày 2 đã merge, build thành công và không còn lỗi tích hợp nghiêm trọng.

## 6. Ngày 3 — Tích hợp, kiểm thử và hoàn thiện

### Nguyễn Quang Vinh

- Backend: rà soát session, filter, phân quyền và exception cho luồng xác thực/khách hàng.
- Frontend: hoàn thiện UX auth/profile/address, lỗi/loading và responsive.
- API: kiểm thử unauthorized/forbidden và sửa lỗi tích hợp auth với các domain khác.

### Nguyễn Minh Huân

- Backend: xử lý trạng thái món không bán, option không hợp lệ và tính nhất quán menu-cart.
- Frontend: hoàn thiện menu/product detail/admin menu, empty state và responsive.
- API: kiểm thử filter/detail/admin CRUD, mã lỗi và dữ liệu món bị vô hiệu hóa.

### Ung Văn Trí

- Backend: tích hợp cart/promotion với checkout, kiểm tra lại voucher và tổng tiền trước khi tạo đơn.
- Frontend: hoàn thiện chuyển tiếp cart → checkout, lỗi voucher và cập nhật tổng tiền.
- API: kiểm thử cart/promotion API, dữ liệu biên và request lặp hoặc hết hiệu lực.

### Nguyễn Đức Phát

- Backend: hoàn thiện transaction order-payment, lịch sử trạng thái và chặn chuyển trạng thái sai.
- Frontend: hoàn thiện checkout/order tracking/admin order processing và trạng thái thanh toán.
- API: kiểm thử end-to-end order/payment/status, sửa lỗi tích hợp và chuẩn bị demo cuối.

### Gate 3 — Definition of Done

- [ ] Luồng end-to-end chạy được: đăng nhập → xem món → thêm giỏ → áp voucher → checkout → thanh toán → theo dõi đơn.
- [ ] Admin/nhân viên xem và cập nhật đơn đúng quyền.
- [ ] Cả bốn thành viên đều có commit backend, frontend và API thực tế.
- [ ] Build sạch, deploy Tomcat thành công và không có lỗi console/server nghiêm trọng.
- [ ] Không commit mật khẩu database, token, secret hoặc dữ liệu cá nhân thật.
- [ ] API có status code, validation và JSON error nhất quán.
- [ ] Test quan trọng cho service, repository và API đều chạy đạt.
- [ ] Cả nhóm review chéo và xác nhận hoàn thành.

## 7. Quy ước phối hợp

- Nguyễn Quang Vinh phụ trách file build/config dùng chung; thay đổi từ người khác cần trao đổi trước.
- Nguyễn Minh Huân phụ trách consistency của component và layout frontend dùng chung.
- Ung Văn Trí phụ trách convention JSON/error dùng chung cho API.
- Nguyễn Đức Phát phụ trách consistency của schema và transaction order-payment.
- Mỗi thành viên tạo branch theo mẫu feature/ten-ngan/domain và pull request nhỏ, dễ review.
- Không merge khi build lỗi; người tạo thay đổi tự sửa conflict trong phạm vi phụ trách.
- Cuối mỗi ngày phải demo, cập nhật checklist Gate và ghi rõ blocker còn lại.

