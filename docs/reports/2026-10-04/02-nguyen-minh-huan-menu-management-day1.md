# Báo cáo: Dựng nền Menu Ngày 1

- Người thực hiện: Nguyễn Minh Huân
- Ngày: 2026-10-04
- Branch: feature/menu-management-day1
- Task/Issue: Ngày 1 — Dựng nền Menu và quản lý món (Category, Food, Food Option)
- Phạm vi báo cáo: Toàn bộ thay đổi của branch `feature/menu-management-day1`

## Mục tiêu

Xây dựng nền tảng và chốt hợp đồng API cho vertical slice Menu và Quản lý món theo đúng phân công Ngày 1:
- Khai báo domain model JPA: Category, Food, Food Option và đăng ký vào persistence unit.
- Thiết lập Repository contracts và JPA implementations cho Category, Food, Food Option.
- Chốt các DTO response model cho danh mục, danh sách món, chi tiết món và tùy chọn món.
- Viết MenuService cung cấp dữ liệu menu mẫu và nghiệp vụ đọc thực đơn.
- Cung cấp các API servlet đọc menu (`/api/categories`, `/api/foods`) và route nền quản trị trả HTTP 501 (`/api/admin/*`).
- Tạo các JSP khung cho thực đơn khách hàng và quản trị món/danh mục đạt tiêu chí Gate 1.

## Đã thực hiện

### Backend

- **Enums**:
  - `FoodStatus` (`AVAILABLE`, `UNAVAILABLE`)
  - `OptionType` (`SIZE`, `TOPPING`, `SUGAR_LEVEL`, `ICE_LEVEL`, `OTHER`)
  - `OptionStatus` (`ACTIVE`, `INACTIVE`)
- **Entities**:
  - `Category`: ánh xạ bảng `category`, quan hệ 1-N với `Food`.
  - `Food`: ánh xạ bảng `food`, quan hệ N-1 với `Category`, 1-N với `FoodOption`.
  - `FoodOption`: ánh xạ bảng `food_option`, quan hệ N-1 với `Food`.
  - Đăng ký 3 entities vào `backend/src/main/resources/META-INF/persistence.xml`.
- **DTOs (Response Models)**:
  - `CategoryResponse`, `FoodSummaryResponse`, `FoodDetailResponse`, `FoodOptionResponse`.
- **Exceptions**:
  - `AppException`, `ResourceNotFoundException`.
- **Repositories**:
  - `CategoryRepository` & `JpaCategoryRepository`: hỗ trợ tìm kiếm, CRUD, kiểm tra trùng tên và sinh mã `DMxx`.
  - `FoodRepository` & `JpaFoodRepository`: hỗ trợ tìm theo danh mục, tìm món đang bán, tìm kiếm từ khóa, fetch eager options và sinh mã `MAxx`.
  - `FoodOptionRepository` & `JpaFoodOptionRepository`: hỗ trợ tìm options theo món, tìm options khả dụng, CRUD và sinh mã `TCxx`.
- **Mappers**:
  - `MenuMapper`: chuyển đổi entity sang DTO response an toàn.
- **Service**:
  - `MenuService`: logic đọc thực đơn cho khách hàng (chỉ lấy món đang bán), danh mục và chi tiết món kèm options.
- **Servlets & API**:
  - `CategoryApiServlet` (`/api/categories`, `/api/categories/*`): phục vụ tra cứu danh mục.
  - `FoodApiServlet` (`/api/foods`, `/api/foods/*`): phục vụ tra cứu thực đơn và chi tiết món.
  - `AdminMenuApiServlet` (`/api/admin/categories/*`, `/api/admin/foods/*`, `/api/admin/options/*`): route nền quản trị, trả HTTP 501 Not Implemented (chờ hoàn thiện ở Ngày 2).
  - `MenuPageServlet` (`/menu`, `/menu/detail`, `/admin/menu`, `/admin/categories`): điều hướng các trang giao diện khung.

### Frontend

- Tạo các file JSP khung kế thừa header/footer chuẩn:
  - `frontend/WEB-INF/views/menu/index.jsp`: Khung danh sách món ăn và danh mục.
  - `frontend/WEB-INF/views/menu/detail.jsp`: Khung chi tiết món và chọn tùy chọn (size/topping).
  - `frontend/WEB-INF/views/admin/menu.jsp`: Khung danh sách món ăn cho trang quản trị.
  - `frontend/WEB-INF/views/admin/categories.jsp`: Khung danh mục món ăn cho trang quản trị.

### Database

- Không thay đổi schema hoặc seed hiện có.
- Tuân thủ ánh xạ các bảng `category`, `food`, `food_option` trong `database/schema.sql`.

### Kiểm thử (Unit Tests)

- `EntityMappingTest`: kiểm tra annotation mapping và quan hệ hai chiều `Category` <-> `Food` <-> `FoodOption`.
- `MenuRouteContractTest`: kiểm tra toàn bộ mapping `@WebServlet` cho các API và trang.
- `MenuServiceTest`: kiểm tra nghiệp vụ đọc danh mục, đọc menu mẫu và chi tiết món.

## File hoặc khu vực đã thay đổi

- `backend/src/main/resources/META-INF/persistence.xml`
- `backend/src/main/java/com/foodordering/enums/`
- `backend/src/main/java/com/foodordering/entity/`
- `backend/src/main/java/com/foodordering/dto/`
- `backend/src/main/java/com/foodordering/exception/`
- `backend/src/main/java/com/foodordering/repository/`
- `backend/src/main/java/com/foodordering/mapper/`
- `backend/src/main/java/com/foodordering/service/`
- `backend/src/main/java/com/foodordering/api/`
- `backend/src/main/java/com/foodordering/servlet/`
- `backend/src/test/java/com/foodordering/`
- `frontend/WEB-INF/views/menu/`
- `frontend/WEB-INF/views/admin/`
- `docs/reports/2026-10-04/02-nguyen-minh-huan-menu-management-day1.md`

## Cách kiểm tra

1. Chạy unit tests:
   ```bash
   mvn -f backend/pom.xml test
   ```
2. Đóng gói WAR:
   ```bash
   mvn -f backend/pom.xml package -DskipTests
   ```
3. Kiểm tra định dạng Git:
   ```bash
   git diff --check
   ```

## Kết quả kiểm tra

- **Maven Test**: Passed toàn bộ 15 tests (0 failure, 0 error, 0 skipped).
- **Maven Package**: Đóng gói thành công `backend/target/crave.war`.
- **Git diff**: Không có lỗi cú pháp hoặc trailing whitespace.

## Ảnh hưởng và lưu ý

- Breaking change: Không.
- Migration cần chạy: Không.
- Cấu hình cần bổ sung: Không.
- Giới hạn hiện tại: Các trang JSP ở Ngày 1 là khung tĩnh theo chuẩn Gate 1; các thao tác Admin CUD và giao diện động sẽ hoàn thiện ở Ngày 2.

## Công việc còn lại

- Triển khai logic CRUD Admin, validation và giao diện tương tác động trên Ngày 2.
