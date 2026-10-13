# Cấu trúc và sơ đồ lớp

Sơ đồ gốc người dùng cung cấp được lưu ở [class-diagram.drawio](class-diagram.drawio),
tải từ [Google Drive](https://drive.google.com/file/d/1eXr3heTw7kpOceJcbZLMliu14QfJW7ZB/view).
Mở bằng diagrams.net để xem đủ các quan hệ và bội số.

Tên lớp Java dùng tiếng Anh; giao diện và thông báo dành cho người dùng dùng tiếng Việt.
Các thao tác trong diagram được thực hiện qua service để kiểm tra quyền, validation và
transaction trước khi cập nhật entity. Số tiền dùng `BigDecimal`/`DECIMAL`, giữ chính xác
khi tính giảm giá, tùy chọn món và thanh toán.

| Nhóm trong sơ đồ | Lớp Java | Quan hệ chính |
|---|---|---|
| Tài khoản và nhân sự | `User`, `Customer`, `Employee`, `Address` | `User` abstract, kế thừa JOINED; khách hàng có nhiều địa chỉ và một giỏ hàng |
| Thực đơn và tùy chọn | `Category`, `Food`, `FoodOption` | Danh mục có nhiều món; món có nhiều tùy chọn |
| Giỏ hàng | `Cart`, `CartItem`, `CartItemOption` | Giỏ thuộc khách hàng; mỗi chi tiết giữ món, số lượng, ghi chú và các tùy chọn |
| Đơn hàng | `CustomerOrder`, `OrderDetail`, `OrderDetailOption`, `OrderStatusHistory` | Đơn thuộc khách hàng, có nhân viên phụ trách, chi tiết và lịch sử; tên/giá món là snapshot khi đặt |
| Khuyến mãi | `Promotion`, `DiscountType`, `PromotionStatus` | Một mã áp dụng cho nhiều đơn; đơn giữ snapshot mức giảm |
| Thanh toán | `Payment`, `PaymentMethod`, `PaymentStatus` | Thanh toán gắn với đơn; hoàn tiền giữ thời điểm thanh toán gốc |

Các FK `promotion_id`, `assigned_employee_id`, `food_id` và `changed_by_employee_id`
được ánh xạ thành quan hệ JPA bên cạnh thuộc tính ID tương thích API hiện có.
Các bảng nối tùy chọn giữ giá tại thời điểm đặt, giúp đơn cũ không thay đổi khi thực đơn đổi giá.

## Cây thư mục

```text
backend/
  pom.xml
  src/main/java/com/foodordering/
    api/                 API JSON
    servlet/             Servlet API đơn hàng và thanh toán
      page/              Toàn bộ servlet hiển thị JSP
    service/             Nghiệp vụ, kiểm tra quyền theo tài khoản, transaction
    repository/          Truy cập JPA và database
    entity/              Domain/JPA theo sơ đồ lớp
    dto/                 Dữ liệu request/response, không trả mật khẩu/hash
    mapper/              Chuyển entity thành DTO
    validator/           Quy tắc dữ liệu đầu vào
    security/            Session và phân quyền
    config/              Persistence pool và vòng đời ứng dụng
    filter/              UTF-8, xác thực và kiểm tra origin
    enums/, exception/, utils/
  src/main/resources/    Persistence và logging
  src/test/java/         Unit/integration tests
  src/dev/java/          Launcher Tomcat và công cụ thiết lập tài khoản, chỉ profile dev
frontend/
  WEB-INF/web.xml        Cấu hình session và trang vào ứng dụng
  WEB-INF/views/         JSP không truy cập trực tiếp
    components/          Header/footer dùng chung
    admin/, auth/, customer/, menu/, cart/, order/, promotions/
  assets/                CSS, JavaScript và hình ảnh
  index.jsp              Trang vào ứng dụng
database/
  schema.sql, seed.sql
  migrations/            Bản cập nhật cho database đã tồn tại
tests/
  frontend/              Kiểm thử trên Chrome/Edge, nằm ngoài WAR
  runtime/               Kiểm tra HTTP trên WAR/Tomcat/MySQL thật
  scripts/               Kiểm tra deploy và rollback bằng fixture
scripts/
  dev/                   Chạy ứng dụng cục bộ
  deploy-ec2.sh          Triển khai WAR lên EC2
docs/
  architecture/          Diagram và ánh xạ lớp
  previews/              Trang mẫu phát triển, nằm ngoài WAR
  api/, project/, reports/
```

`backend/target/`, `.runtime/`, `node_modules/` và cấu hình `.env` là dữ liệu cục bộ,
không đưa lên Git. WAR chỉ chứa frontend chạy thật, lớp ứng dụng và thư viện runtime.
