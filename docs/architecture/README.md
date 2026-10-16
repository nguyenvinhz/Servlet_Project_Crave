# Kiến trúc và sơ đồ lớp

Ứng dụng hiện dùng **Servlet + JSP/JSTL + JPA/Hibernate + HikariCP + MySQL**. Xem [luồng request, form và route hiện hành](servlet-jsp.md).

Sơ đồ nghiệp vụ gốc nằm trong [class-diagram.drawio](class-diagram.drawio). Các quan hệ nghiệp vụ được giữ khi đổi cấu trúc Java; sơ đồ gốc không mô tả đầy đủ tên package mới.

## Phân chia mã nguồn

```text
backend/src/main/java/com/foodordering/
  servlet/
    account/       AuthServlet, ProfileServlet, AddressServlet, EmailVerificationServlet
    admin/         AdminServlet, AdminMenuServlet, AdminAccountServlet, AdminPromotionServlet
    ...            MenuServlet, CartServlet, CheckoutServlet, OrderServlet,
                   AdminOrderServlet, PaymentServlet, PayosPaymentServlet, HealthServlet
  filter/          UTF-8, xác thực, phân quyền và nguồn gửi form
  listener/        Khởi tạo/đóng database và cấu hình cookie session
  bean/            *Form cho đầu vào; *Bean cho dữ liệu hiển thị
  model/           Entity và quan hệ JPA
  dao/             *DAO và Jpa*DAO
  service/         Nghiệp vụ, transaction, kiểm tra sở hữu và tính tiền
  validator/       Quy tắc đầu vào
  integration/     payOS, geocoder; mail/ cho SMTP
  config/          DatabaseConfig, EmailConfig, PayosConfig
  security/        SessionAuth
  enums/
  exception/
  util/
backend/src/main/resources/META-INF/persistence.xml
frontend/WEB-INF/views/
  components/
  admin/, auth/, customer/, menu/, cart/, order/, promotions/
frontend/assets/   CSS, JavaScript giao diện, ảnh và Leaflet
```

`listener` quản lý vòng đời `EntityManagerFactory`; HikariCP giữ pool kết nối. DAO và các service thống kê/quản trị dùng `EntityManager` trong phạm vi xử lý, đóng sau khi hoàn tất. `persistence.xml` tham chiếu entity trong `model`, dùng `RESOURCE_LOCAL` và xác minh schema hiện có.

Servlet đọc parameter, gọi service và đặt bean lên request để JSP hiển thị. Form không được bind bằng JSON hay thư viện mapper. Việc tạo bean hiển thị được viết trực tiếp trong mã nghiệp vụ. JavaScript không gọi API JSON của ứng dụng.

## Quan hệ nghiệp vụ

| Nhóm | Entity trong `model` | Quan hệ chính |
|---|---|---|
| Tài khoản, nhân sự | `User`, `Customer`, `Employee`, `Address` | `User` abstract, kế thừa JOINED; khách có nhiều địa chỉ và một giỏ |
| Thực đơn | `Category`, `Food`, `FoodOption` | Danh mục có nhiều món; món có nhiều tùy chọn |
| Giỏ | `Cart`, `CartItem`, `CartItemOption` | Thuộc khách; chi tiết giữ số lượng, ghi chú và tùy chọn |
| Đơn | `CustomerOrder`, `OrderDetail`, `OrderDetailOption`, `OrderStatusHistory` | Giữ khách, nhân viên, chi tiết và lịch sử; tên/giá món lưu snapshot |
| Khuyến mãi | `Promotion` | Áp dụng cho nhiều đơn; đơn giữ snapshot mức giảm |
| Thanh toán | `Payment`, `PayosPaymentLink` | Liên kết đơn với trạng thái thanh toán và dữ liệu đối soát payOS |

Số tiền dùng `BigDecimal`/`DECIMAL`. Tên/giá món và tùy chọn của đơn đã đặt không thay đổi theo thực đơn hiện tại. Việc đổi package Java không đổi tên bảng, cột, khóa ngoại hoặc dữ liệu hiện có.

## Đổi tên từ cấu trúc cũ

| Trước đây | Hiện tại |
|---|---|
| `api/*ApiServlet` và `servlet/page/*PageServlet` | Servlet theo chức năng, cùng xử lý GET và form POST |
| `dto/*Request` | `bean/*Form` |
| `dto/*Response`, `dto/*Dto` | `bean/*Bean` |
| `entity` | `model` |
| `repository/*Repository` | `dao/*DAO` |
| `config/*Listener` | `listener` |
| `gateway`, `mail` | `integration`, `integration/mail` |
| `utils` | `util` |
| `mapper` | Đã bỏ; tạo bean trực tiếp trong service |
| JSON giữa trình duyệt và backend | HTML, form parameter, redirect hoặc forward |

Các file trong `docs/api` và báo cáo cũ giữ giá trị lịch sử; hợp đồng JSON của thiết kế cũ đã được thay bằng [route Servlet và form](servlet-jsp.md).

`backend/target/`, `.runtime/`, `node_modules/` và `.env` là dữ liệu local. WAR chứa JSP/assets, lớp ứng dụng và thư viện runtime; kiểm thử, preview và launcher phát triển nằm ngoài phần triển khai.
