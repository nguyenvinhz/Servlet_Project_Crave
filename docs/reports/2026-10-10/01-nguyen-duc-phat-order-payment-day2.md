# Báo cáo tiến độ - Day 2: Order & Payment

**Người thực hiện:** Nguyễn Đức Phát
**Ngày báo cáo:** 2026-10-10
**Module:** Order & Payment

## 1. Các công việc đã thực hiện trong Day 2

### Backend
- **Đồng bộ nhánh (Resolve Conflicts):** Đã resolve conflict giữa nhánh `feature/order-payment-day2` và `main` (chứa các bản vá giao diện từ Day 1). Giữ lại logic xử lý phức tạp của Day 2 và gộp với HTML/CSS của Day 1.
- **Tích hợp Auth chính thức:** Đã xóa bỏ các mock query parameters (`mock_customer`, `mock_employee`) và áp dụng `SessionAuth` để xác thực qua Cookie/Session thật của hệ thống. Kế thừa `BaseApiServlet` để chuẩn hóa các lỗi `401 Unauthorized` và `403 Forbidden`.
- **Sửa lỗi biên dịch (Compilation):** Khắc phục triệt để 36 lỗi biên dịch do TypeMismatch trong `CustomerOrderServiceImpl` (sai sót khi dùng `.name()` cho thuộc tính Enum lúc mapping DTO).
- **Ngăn chặn Transaction Lazy Loading:** Đã bổ sung quản lý vòng đời `EntityManager` trực tiếp trong Service (`CustomerOrderServiceImpl`) thay vì dùng try-with-resources ngắn hạn ở Repository, qua đó khắc phục lỗi `LazyInitializationException` khi map collection (như `details`, `payment`, `history`) vào DTO.
- **Sửa lỗi 403 API Thanh toán:** Đã đổi đường dẫn `PaymentServlet` sang `/api/admin/payments/*` để vượt qua `AuthenticationFilter` một cách hợp lệ (vì Filter đang tự động chặn các đường dẫn không có chữ `admin` nếu là account Nhân viên).
- **Validation Dữ liệu Database:** Chặn triệt để việc khách hàng đặt món ăn đã hết (`UNAVAILABLE`) bằng exception từ backend. Đồng thời bổ sung query kiểm tra trực tiếp trạng thái của Voucher trong database, nếu Voucher `INACTIVE` thì sẽ báo lỗi không cho đặt, ngăn chặn tình trạng khách hách giảm giá.
- **Xử lý bất đồng bộ (Concurrency Checkout):** Cập nhật phương thức `createOrder` sử dụng `FOR UPDATE` khóa row `cart` lại, phòng tránh triệt để lỗi submit đúp sinh ra nhiều đơn hàng từ một giỏ. 
- **Hoàn thiện Logic Payment Amount và Phí Ship:** Tiền thanh toán giờ đây được tính chính xác (bao gồm xử lý Subtotal, Delivery Fee, và tự động gọi Database trigger để áp Discount Amount qua Promotion ID). Đặc biệt, hệ thống đã bắt logic 0đ phí ship nếu chọn Nhận tại quán (PICKUP), thay vì fix cứng 15,000đ như trước.

### Frontend
- **Sửa lỗi mất Voucher khi Checkout:** Bổ sung việc trích xuất `promotionId` từ dữ liệu Cart trả về và đưa ngược vào Payload `POST /api/orders` để backend nắm được mã giảm giá. Script cũng đã bổ sung hiển thị trực quan số tiền được giảm ngay trên màn hình tóm tắt đơn.
- **Cập nhật động Phí Giao Hàng:** Giao diện `checkout.js` giờ đã tự động nhảy về 0đ đối với phí giao hàng khi khách chọn "Nhận tại cửa hàng" (PICKUP).
- **Di dời Logic JavaScript (Fix HTTP 500):** Tách toàn bộ các block `<script>` nội tuyến khỏi `orders.jsp`, `order-detail.jsp`, `admin-orders.jsp` và `checkout.jsp` sang thư mục `frontend/assets/js/`. Nguyên nhân là do cú pháp JS Template Literal (dùng `${...}`) bị JSP Engine hiểu nhầm là Expression Language (EL), dẫn đến lỗi server khi render.
- **Ngăn chặn Stored XSS:** Viết lại phương thức render DOM trong các file JavaScript, sử dụng `document.createElement` và `textContent` thay cho `innerHTML` khi chèn các dữ liệu từ người dùng (như `receiverName`, `customerNote`, `deliveryAddress`), đảm bảo chống tấn công Stored XSS triệt để.
- **Thanh toán (checkout.jsp):** Đoạn gọi API lấy giỏ hàng (Cart API) và Promotion hiện đang là **giả lập/mock**. Hiện API Giỏ hàng và Khuyến mãi của team chưa hoàn thiện nên Frontend sẽ dùng fetch mock để mô phỏng dữ liệu và tính phí giao hàng. Báo cáo rõ đây chỉ là giả lập.
- **Quản lý đơn hàng Admin:** Xóa logic gửi parameter giả lập, gọi đúng endpoint `PATCH /crave/api/admin/orders/{orderId}/status`.

## 2. Kết quả đạt được
- Hệ thống đã liên kết hoàn chỉnh luồng từ Frontend -> API -> Service -> DB, xác thực dựa vào `SessionAuth` thật 100%.
- Không phát sinh bất kỳ lỗi biên dịch nào trên Maven (`mvn compile` success).
- Hạn chế tối đa các lỗi bảo mật (Double submit, XSS, Lazy Loading exceptions).

## 3. Khó khăn / Vấn đề tồn đọng
- Module Cart và Promotion (khuyến mãi) từ Trí chưa hoàn thành, do đó luồng Checkout phải tích hợp qua API giả lập tại JS (`fetch('/crave/api/cart')`). Khi các API này sẵn sàng, cần review lại schema JSON response để map cho khớp.
- Chưa test End-to-End được sâu với Role Admin vì luồng Login tạo role đang đợi từ Vinh hoàn tất. 

## 4. Kế hoạch tiếp theo (Day 3)
- Chuẩn bị code review, tạo Pull Request và merge.
- Chạy tích hợp toàn bộ các branch để test end-to-end với dữ liệu Login và Session của team.
