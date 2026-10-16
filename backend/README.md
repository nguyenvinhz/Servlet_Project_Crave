# Backend Servlet/JSP

Backend là ứng dụng Maven WAR dùng Jakarta Servlet 6, JSP/JSTL, JPA 3.1 với Hibernate và connection pool HikariCP. Không dùng Spring MVC, REST controller, mapper hoặc JSON để nhận/trả dữ liệu trang web.

```text
backend/
  pom.xml
  src/main/java/com/foodordering/
    servlet/                 Servlet khách hàng, cửa hàng, đơn hàng, payOS, health
      account/               Servlet đăng nhập, OTP, hồ sơ và địa chỉ
      admin/                 Admin*Servlet cho toàn bộ chức năng quản trị
    bean/                    *Form nhận dữ liệu, *Bean hiển thị JSP
    model/                   Các entity JPA ánh xạ database
    dao/                     *DAO và Jpa*DAO đọc/ghi dữ liệu
    service/                 Nghiệp vụ và transaction JPA
    validator/               Kiểm tra dữ liệu và quy tắc nghiệp vụ
    filter/                  Filter xác thực, phân quyền và UTF-8
    listener/                ServletContextListener quản lý pool và session
    config/                  Cấu hình JPA, HikariCP và dịch vụ ngoài
    security/                Xử lý session đăng nhập
    enums/                   Kiểu trạng thái và vai trò
    exception/               Lỗi nghiệp vụ
    integration/             payOS, geocoder và SMTP
    util/                    Hàm hỗ trợ
  src/main/resources/
    META-INF/persistence.xml Cấu hình persistence unit và entity
  src/main/webapp/
    WEB-INF/web.xml          Descriptor Servlet 6 của backend
  src/test/java/             Kiểm thử theo package sản xuất
  src/dev/java/              Launcher/công cụ local, không đóng gói vào WAR
```

Servlet có tên lớp và tên `@WebServlet` giống nhau, kết thúc bằng `Servlet`, xử lý `doGet`/`doPost`. GET đặt dữ liệu bằng request attribute rồi forward JSP; POST đọc `getParameter`, xử lý nghiệp vụ rồi redirect hoặc hiển thị lại form lỗi. Các lớp DAO, bean, model và service giữ tên theo trách nhiệm của chúng.

Maven đóng gói JSP/assets đang có trong `../frontend` qua `webResources`, không sửa hoặc di chuyển frontend. Descriptor có hiệu lực là `src/main/webapp/WEB-INF/web.xml`; bản descriptor trong frontend được loại khỏi tài nguyên bổ sung để tránh ghi đè.

`DatabaseConfig` tạo `EntityManagerFactory` và cấu hình `org.hibernate.hikaricp.internal.HikariCPConnectionProvider`. `DatabaseContextListener` kiểm tra kết nối khi khởi động và đóng factory/pool khi dừng. EntityManager được mở/đóng theo thao tác, không dùng chung giữa request.

JSON chỉ phục vụ giao thức bên ngoài của payOS/geocoder và webhook payOS. Các URL trang/form không đổi khi chuẩn hóa tên và package. `/api/health` là alias tương thích của `/health`, trả văn bản thuần.

Chạy kiểm thử với MySQL dành riêng cho test đã nạp schema/seed:

```powershell
mvn -f backend/pom.xml -Pdev clean verify
```

`ServletStructureTest` kiểm tra tên/package, URL không trùng, cấu trúc WAR, JPA và ranh giới JSON. `JpaHikariIntegrationTest` kiểm tra trực tiếp provider/pool/kết nối đang chạy. WAR xuất ra `target/crave.war`.
