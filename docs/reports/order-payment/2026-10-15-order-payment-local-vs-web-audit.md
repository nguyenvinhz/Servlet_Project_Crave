# Rà soát Order & Payment: local và web — 15/10/2026

Phạm vi: đọc code local, kiểm tra trình duyệt/API local và web `http://15.134.39.86:8080/crave`, chạy kiểm thử và tái hiện nghiệp vụ bằng dữ liệu riêng trong `crave_local_test`. Không tạo/sửa/hủy đơn trên web, không thay trạng thái thanh toán trên web và không chuyển tiền thật. Dữ liệu kiểm thử local được dọn bằng cơ chế fixture hiện có; không thay giỏ hàng hay đơn của tài khoản đang demo.

## Kết quả theo ưu tiên

### 1. Bản web chưa có các sửa đổi giao hàng và giỏ hàng mới — cần đồng bộ trước demo

**Xác nhận trên web và so sánh local.** Script checkout web vẫn tính phí giao tận nơi bằng hằng số 15000; cart cũng cộng 15000. Web không có API `/api/delivery/policy` (404), trong khi local có route được bảo vệ đăng nhập (401 khi chưa đăng nhập). DTO đơn trên web không có các trường tọa độ mới. Script cart/checkout hai môi trường khác nhau.

Hành động: hoàn thiện bản sửa, chạy migration tọa độ trước WAR rồi triển khai cùng một phiên bản lên web. Không chỉ chép JavaScript: backend và schema phải đi cùng. Kiểm thử lại checkout, chỉnh món trong giỏ, miễn phí ship và giới hạn giao hàng trên web sau triển khai. Các đơn lịch sử giữ nguyên phí đã lưu.

### 2. Local chưa sẵn sàng thanh toán QR nhưng checkout vẫn cho chọn — rủi ro demo

**Xác nhận bằng trình duyệt/API local.** API payOS trả 503 `PAYOS_NOT_CONFIGURED`; checkout vẫn bật `BANK_TRANSFER` và `E_WALLET`. `createOrder` tạo đơn và xóa giỏ trước khi trang chi tiết tạo link QR. Vì vậy đơn có thể được tạo nhưng khách không có QR để trả tiền. Quy trình này cũng chưa có đổi sang tiền mặt cho đơn đã tạo.

Hành động: nếu demo QR thì cấu hình và kiểm thử đúng môi trường; nếu chưa có cấu hình thì vô hiệu hóa hai lựa chọn trước khi đặt hàng, kèm thông báo. Giữ luồng tiền mặt hoạt động.

**Chưa xác minh giao dịch QR thật và webhook trên web.** Health check UP không chứng minh payOS đã hoạt động đầy đủ. Không tạo link hay gọi API đồng bộ thanh toán của đơn thật trên web trong lần rà soát này.

### 3. Đơn chuyển khoản chưa trả tiền vẫn hoàn tất được — thiếu điều kiện nghiệp vụ

**Tái hiện trên MySQL local bằng fixture riêng.** Tạo đơn `BANK_TRANSFER`, chuyển `PENDING_CONFIRMATION → PREPARING → COMPLETED`: kết quả `COMPLETED`, thanh toán vẫn `PENDING`. Service chỉ kiểm tra đường chuyển trạng thái đơn, không kiểm tra trạng thái thanh toán tại bước hoàn tất.

Hành động: chốt và thực thi quy tắc ở backend. Đề xuất không cho hoàn tất đơn khi chưa xác nhận thu tiền; với tiền mặt có thể xác nhận thu tiền ngay trong thao tác hoàn tất, trong một transaction. Quy tắc cho chuẩn bị/giao đơn online trước khi thu tiền cũng cần được mô tả nhất quán.

Tham chiếu: `CustomerOrderServiceImpl.updateOrderStatus`, `frontend/assets/js/admin-orders.js`.

**Chưa thử chuyển trạng thái đơn thật trên web.** Không suy diễn rằng dữ liệu hiện có đã vi phạm: 21 đơn web được đọc không có đơn COMPLETED mà thanh toán chưa SUCCESS.

### 4. Nhân viên có thể đánh dấu đã trả tiền cho đơn online bằng tay — cần phân biệt đối soát

**Tái hiện trên local.** `PaymentServiceImpl.updatePaymentStatus` nhận đơn `BANK_TRANSFER` đang PENDING và chuyển SUCCESS mà không gọi payOS. **Trình duyệt cả local/web** hiển thị lựa chọn SUCCESS cho thanh toán online đang chờ. Không thực hiện cập nhật thật trên web.

Hành động: thông thường tiền mặt cho phép xác nhận thủ công; QR cần xác nhận từ gateway. Nếu cần đối soát/chuyển khoản ngoài gateway thì làm thành luồng riêng, có lý do, người thực hiện và bằng chứng giao dịch. Hiện lịch sử đơn không thay thế được lịch sử chỉnh thanh toán.

Tham chiếu: `PaymentServlet`, `PaymentServiceImpl`, `admin-orders.js`.

### 5. Trang quản trị hiển thị sai phương thức thanh toán — lỗi đã xác nhận ở cả hai môi trường

Backend trả `CASH`, nhưng giao diện kiểm tra `method === 'COD'`. Trình duyệt thực tế hiển thị **Chuyển khoản** cho đơn CASH ở cả local và web. `E_WALLET` cũng bị gộp thành Chuyển khoản.

Hành động: ánh xạ đúng ba enum CASH/BANK_TRANSFER/E_WALLET tại bảng đơn và modal chi tiết. Test admin hiện dùng fixture `COD`, nên không phát hiện lỗi khác biệt với backend thật.

### 6. Hoàn tiền hiện là ghi nhận thủ công, chưa phải giao dịch hoàn tiền tự động

**Xác nhận từ code/tài liệu local.** Chuyển `SUCCESS → REFUNDED` chỉ cập nhật bản ghi payment; chưa có lệnh chuyển tiền hoàn trả qua payOS. Tài liệu `docs/project/payos.md` cũng nêu giới hạn này. Không đánh dấu hoàn tiền cho đơn thật trong lần rà soát.

Hành động trước vấn đáp: trình bày rõ nhân viên hoàn tiền ngoài hệ thống rồi ghi nhận; chỉ hiển thị “đã hoàn tiền” sau khi có xác nhận thực tế. Không cần bổ sung cổng hoàn tiền mới chỉ để demo.

### 7. Giới hạn ghi chú không khớp — lỗi nhập liệu nhỏ

**Xác nhận local.** Textarea checkout cho 500 ký tự, `OrderValidator` và cột dữ liệu chỉ nhận 255. Probe với 256 ký tự bị từ chối. Chưa kiểm chứng form khách hàng của web bằng tài khoản customer.

Hành động: thống nhất 255 ký tự ở giao diện/backend/schema và hiển thị giới hạn trước khi gửi.

## Phần đã kiểm tra đạt và giới hạn kết luận

- Health check application/database: UP ở cả local và web.
- API đơn yêu cầu đăng nhập ở cả hai môi trường.
- Đọc 21 đơn web và 15 đơn local: số tiền payment khớp totalAmount; không thấy đơn hoàn tất chưa SUCCESS trong tập dữ liệu hiện có. Đây là kiểm tra snapshot, không chứng minh mọi đường chuyển trạng thái đều an toàn.
- 75 kiểm thử backend đã qua, gồm service đơn/thanh toán, phân quyền servlet, serialization, payOS service và 3 bài kiểm thử payOS với MySQL thật. Nhà cung cấp thanh toán trong các bài test là mock/fixture.
- 10/10 bài kiểm thử trình duyệt payOS đã qua: QR, số tiền server, retry, polling, trạng thái kết thúc, URL thanh toán và hủy controller.
- Probe riêng với MySQL xác nhận các vấn đề 3/4/7; fixture đã được dọn.
- Rà soát không chứng nhận giao dịch tiền thật, khả năng nhận webhook trên web, hoặc mọi trường hợp đồng thời giữa nhân viên/gateway/hủy đơn.

Các lỗi/nghiệp vụ trong báo cáo chưa được sửa ở lượt rà soát này. Ưu tiên thực tế trước vấn đáp: đồng bộ phiên bản triển khai, quyết định rõ demo tiền mặt hay QR, chặn hoàn tất/đánh dấu thanh toán sai luồng, rồi sửa nhãn phương thức và giới hạn ghi chú.
