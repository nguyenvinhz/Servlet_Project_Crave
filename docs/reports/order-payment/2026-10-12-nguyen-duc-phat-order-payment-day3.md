# Báo cáo: Tích hợp, kiểm thử và hoàn thiện Đơn hàng & Thanh toán Ngày 3

- Người thực hiện: Nguyễn Đức Phát
- Ngày: 2026-10-12
- Branch: feature/order-payment-day3
- Task/Issue: Ngày 3 — Tích hợp, kiểm thử và hoàn thiện theo vertical slice Đơn hàng & Thanh toán
- Phạm vi báo cáo: Toàn bộ branch `feature/order-payment-day3`

---

## Mục tiêu

Hoàn thành toàn diện các nhiệm vụ Ngày 3 theo bản phân công nghiệp vụ và tiêu chuẩn nghiệm thu **Gate 3 (Definition of Done)**:
- **Backend:** Củng cố tính nguyên tử (atomic) của transaction đặt hàng; hoàn thiện máy trạng thái đơn hàng (Order State Machine) chặn đứng mọi chuyển đổi phi logic; chuẩn hóa xử lý ngoại lệ nghiệp vụ (`ResourceNotFoundException`, `BadRequestException`) theo schema chuẩn OpenAPI; hỗ trợ tiêm phụ thuộc `EntityManagerFactory` giúp module có thể kiểm thử đơn vị độc lập.
- **Frontend:** Loại bỏ triệt để việc hardcode đường dẫn ngữ cảnh (`/crave/`), tích hợp dynamic context path trên toàn bộ các trang và script của phân hệ; nâng cấp giao diện Theo dõi đơn hàng (`/orders/{id}`) với thanh tiến trình trực quan (Order Stepper Timeline); bổ sung Empty State sinh động cho Lịch sử đơn hàng (`/orders`); hoàn thiện bảng điều khiển Quản trị đơn hàng (`/admin/orders`) hỗ trợ cập nhật song song trạng thái đơn hàng và trạng thái thanh toán.
- **API & Phân quyền:** Tích hợp xác thực phiên an toàn qua `SessionAuth`: bắt buộc khách hàng đăng nhập khi checkout và xem lịch sử đơn; kiểm tra quyền sở hữu đơn hàng (Ownership Check - trả HTTP 403 khi truy cập đơn của khách khác); bảo vệ các endpoint quản trị bằng vai trò `EmployeeRole.ORDER_STAFF`.
- **Kiểm thử:** Bổ sung đầy đủ Unit Test suite cho `CustomerOrderService` và `PaymentService`, đạt tỷ lệ thành công 100% trên toàn bộ các bộ test của phân hệ Đơn hàng & Thanh toán.

---

## Đã thực hiện

### 1. Backend & Nghiệp vụ

- **Hoàn thiện tính nguyên tử của Transaction Đặt hàng (`CustomerOrderServiceImpl.createOrder`):**
  - Khóa giỏ hàng bằng câu lệnh `SELECT ... FOR UPDATE` để ngăn ngừa race condition và tình trạng đặt trùng lặp.
  - Snapshot toàn bộ giá, tên món và tùy chọn (`OrderDetailOption`) trực tiếp từ cơ sở dữ liệu thay vì tin cậy dữ liệu client gửi lên.
  - Tự động khởi tạo bản ghi thanh toán (`Payment`) ban đầu với trạng thái `PENDING`.
  - Ghi nhận lịch sử trạng thái đầu tiên (`OrderStatusHistory` với trạng thái `PENDING_CONFIRMATION`).
  - Xóa sạch giỏ hàng trong cùng một giao dịch; rollback toàn diện nếu phát hiện lỗi giỏ hàng trống, món hết hàng hoặc voucher không hợp lệ.
- **Củng cố Máy trạng thái Đơn hàng (Order State Machine - `updateOrderStatus`):**
  - Kiểm soát nghiêm ngặt các bước chuyển trạng thái hợp lệ:
    - `PENDING_CONFIRMATION` ➔ `PREPARING` hoặc `CANCELLED`
    - `PREPARING` ➔ `DELIVERING` hoặc `CANCELLED`
    - `DELIVERING` ➔ `COMPLETED`
  - Chặn tuyệt đối việc thay đổi trạng thái khi đơn đã ở `COMPLETED` hoặc `CANCELLED`, ném ngoại lệ `BadRequestException` với thông báo tiếng Việt rõ ràng.
  - Lưu đầy đủ dấu vết kiểm toán trong `OrderStatusHistory`: mã nhân viên thao tác (`changedByEmployeeId`), thời điểm (`changedAt`), và ghi chú (`note`).
- **Nghiệp vụ Cập nhật Thanh toán (`PaymentServiceImpl.updatePaymentStatus`):**
  - Hỗ trợ cập nhật trạng thái thanh toán (`PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`).
  - Tự động cập nhật thời điểm thanh toán (`paidAt`) khi trạng thái là `SUCCESS` hoặc `REFUNDED`.
  - Ghi nhận mã tham chiếu giao dịch (`transactionRef`) từ cổng thanh toán/ngân hàng.
- **Khả năng kiểm thử (Testability):**
  - Bổ sung constructor nhận `EntityManagerFactory` trong `CustomerOrderServiceImpl` và `PaymentServiceImpl`, cho phép mock kết nối database trong Unit Tests mà không phá vỡ tính tương thích ngược với code đang chạy.

### 2. Frontend & UX/UI

- **Chuẩn hóa Context Path:**
  - Thay thế toàn bộ các đường dẫn tĩnh `/crave/` trong `orders.js`, `order-detail.js`, `admin-orders.js` và các file JSP bằng dynamic context path (`${pageContext.request.contextPath}` và `document.body.dataset.contextPath || ''`). Ứng dụng chạy mượt mà trên cả máy dev lẫn EC2 production (root path `/`).
- **Trang Chi tiết & Theo dõi Đơn hàng (`order-detail.jsp` & `order-detail.js`):**
  - **Thanh Stepper Timeline 4 bước:** Hiển thị trực quan quy trình: *1: Chờ xác nhận ➔ 2: Đang chuẩn bị ➔ 3: Đang giao hàng ➔ 4: Hoàn tất*. Các bước đã hoàn thành được đánh dấu tick `✓`, bước hiện tại được kích hoạt hiệu ứng phát sáng `accent`.
  - **Cảnh báo đơn bị hủy:** Hiển thị banner màu đỏ chuyên biệt khi đơn hàng ở trạng thái `CANCELLED`.
  - **Lưới thông tin:** Bố cục 2 cột hiển thị đầy đủ thông tin giao nhận, địa chỉ, phương thức thanh toán và trạng thái thanh toán.
  - **Chi tiết món ăn:** Danh sách món kèm tùy chọn (topping, size...) và đơn giá phụ thu snapshot.
  - **Bảng tổng kết:** Tạm tính, phí giao hàng, voucher giảm giá và tổng tiền thanh toán cuối cùng.
  - **Kiểm soát quyền xem đơn:** Hiển thị giao diện thông báo 403 thân thiện nếu người dùng cố ý truy cập URL đơn hàng của tài khoản khác.
- **Trang Lịch sử Đơn hàng (`orders.jsp` & `orders.js`):**
  - Danh sách card đơn hàng thiết kế theo chuẩn Dark Theme (`order.css`).
  - Badge trạng thái đồng bộ màu sắc (`badge-pending`, `badge-preparing`, `badge-delivering`, `badge-completed`, `badge-cancelled`).
  - Khối **Empty State** sinh động khi chưa có đơn nào, kèm nút điều hướng đến trang thực đơn.
- **Trang Quản trị Đơn hàng (`admin-orders.jsp` & `admin-orders.js`):**
  - Bảng danh sách đơn hàng cho nhân viên xử lý với giao diện Dark Table.
  - Dropdown cập nhật trạng thái đơn hàng kèm nút "Lưu đơn". Nếu nhân viên chọn sai quy trình State Machine, thông báo lỗi cụ thể từ server sẽ được hiển thị ngay trên banner cảnh báo.
  - Dropdown cập nhật trạng thái thanh toán kèm nút "Lưu TT".
  - Nút tải lại danh sách không cần F5 trình duyệt.

### 3. API & Bảo Mật

- **`OrderServlet` (`/api/orders/*`):**
  - `POST /api/orders`: Yêu cầu xác thực `SessionAuth.requireCustomer(req)`. Nhận payload tạo đơn và trả về HTTP 201 kèm mã đơn hàng.
  - `GET /api/orders`: Trả về danh sách đơn hàng thuộc sở hữu của khách hàng đang đăng nhập.
  - `GET /api/orders/{id}`: Kiểm tra quyền sở hữu đơn hàng (`customerId`). Trả về HTTP 403 nếu đơn thuộc khách hàng khác.
- **`AdminOrderServlet` (`/api/admin/orders/*`):**
  - `GET /api/admin/orders`: Yêu cầu vai trò `EmployeeRole.ORDER_STAFF`. Trả về toàn bộ danh sách đơn hàng cho nhân viên.
  - `PATCH /api/admin/orders/{id}/status`: Yêu cầu `ORDER_STAFF`. Validate request body và thực hiện chuyển trạng thái theo đúng máy trạng thái.
- **`PaymentServlet` (`/api/admin/payments/*`):**
  - `PATCH /api/admin/payments/{id}/status`: Yêu cầu `ORDER_STAFF`. Cập nhật trạng thái thanh toán và mã giao dịch.

---

## Kiểm thử (Tests)

Đã bổ sung và chạy kiểm thử toàn diện cho phân hệ:
1. **`CustomerOrderServiceTest`** (12 tests):
   - Kiểm tra validation tạo đơn hàng (thiếu tên, thiếu SĐT, thiếu địa chỉ khi giao tận nơi).
   - Kiểm tra lấy chi tiết đơn ném `ResourceNotFoundException` khi không tồn tại.
   - Kiểm tra State Machine: chuyển từ `PENDING` sang `PREPARING` thành công.
   - Kiểm tra State Machine: chuyển từ `PREPARING` sang `DELIVERING` thành công.
   - Kiểm tra State Machine: chuyển từ `DELIVERING` sang `COMPLETED` thành công.
   - Kiểm tra State Machine: chặn chuyển trạng thái nhảy cóc (ví dụ `PENDING` sang `COMPLETED`).
   - Kiểm tra State Machine: chặn cập nhật đơn đã `COMPLETED` hoặc `CANCELLED`.
2. **`PaymentServiceTest`** (4 tests):
   - Kiểm tra cập nhật thanh toán ném `ResourceNotFoundException` khi mã thanh toán không tồn tại.
   - Kiểm tra cập nhật sang `SUCCESS` thành công, lưu `transactionRef` và thời điểm `paidAt`.
   - Kiểm tra cập nhật sang `FAILED` thành công.
3. **`AuthenticatedOrderPaymentServletTest`** (18 tests):
   - Kiểm tra xác thực phiên khách hàng và nhân viên cho tất cả các endpoint.
   - Kiểm tra chặn khách hàng truy cập API admin và chặn nhân viên tạo đơn giả mạo.
   - Kiểm tra quyền sở hữu đơn hàng (Ownership check trả 403).
   - Kiểm tra xử lý mã lỗi chuẩn OpenAPI (400, 401, 403, 404, 500).
4. **`OrderResponseSerializationTest`** (2 tests):
   - Khóa hợp đồng JSON response của DTO đơn hàng.
5. **`CustomerOrderCompatibilityTest`** (13 tests):
   - Kiểm tra tính tương thích và cấu trúc của CustomerOrder Entity.

**Tổng số test phân hệ chạy thành công:** **49 / 49 tests (0 failure, 0 error, 0 skipped)**.  
**Biên dịch toàn dự án:** `mvn test-compile` ➔ **BUILD SUCCESS**.

---

## Danh sách file đã thay đổi và bổ sung

1. **Backend Service & Exception Handling:**
   - [`CustomerOrderServiceImpl.java`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/backend/src/main/java/com/foodordering/service/CustomerOrderServiceImpl.java)
   - [`PaymentServiceImpl.java`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/backend/src/main/java/com/foodordering/service/PaymentServiceImpl.java)
2. **Frontend Views & Scripts:**
   - [`orders.jsp`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/frontend/WEB-INF/views/order/orders.jsp)
   - [`order-detail.jsp`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/frontend/WEB-INF/views/order/order-detail.jsp)
   - [`admin-orders.jsp`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/frontend/WEB-INF/views/admin/admin-orders.jsp)
   - [`orders.js`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/frontend/assets/js/orders.js)
   - [`order-detail.js`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/frontend/assets/js/order-detail.js)
   - [`admin-orders.js`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/frontend/assets/js/admin-orders.js)
3. **Unit Tests:**
   - [`CustomerOrderServiceTest.java`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/backend/src/test/java/com/foodordering/service/CustomerOrderServiceTest.java)
   - [`PaymentServiceTest.java`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/backend/src/test/java/com/foodordering/service/PaymentServiceTest.java)
4. **Báo cáo nghiệm thu:**
   - [`2026-10-12-nguyen-duc-phat-order-payment-day3.md`](file:///d:/TAI_LIEU_HOC_TAP_DAI_HOC/Lap%20trinh%20Web/CRAVE/Servlet_Project_Crave/docs/reports/order-payment/2026-10-12-nguyen-duc-phat-order-payment-day3.md)

---

## Kết luận & Đánh giá Gate 3
Phân hệ Đơn hàng & Thanh toán đã hoàn tất 100% mục tiêu của Ngày 3, đáp ứng trọn vẹn tiêu chuẩn Definition of Done (Gate 3). Codebase sẵn sàng cho việc nghiệm thu, tích hợp CI và triển khai tự động lên hệ thống EC2.
