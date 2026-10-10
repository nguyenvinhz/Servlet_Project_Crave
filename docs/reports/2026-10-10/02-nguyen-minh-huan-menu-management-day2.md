# Báo cáo: Hoàn thiện chức năng Menu và Quản lý món Ngày 2

- Người thực hiện: Nguyễn Minh Huân
- Ngày: 2026-10-10
- Branch: feature/menu-management-day2
- Task/Issue: Ngày 2 — Hoàn thiện chức năng theo vertical slice Menu & Quản lý món
- Phạm vi báo cáo: Toàn bộ branch `feature/menu-management-day2`

## Mục tiêu

Hoàn thiện toàn diện chức năng vertical slice Menu và Quản lý món theo kế hoạch Ngày 2 và tiêu chí nghiệm thu Gate 2:
- Backend: Hoàn thiện đọc menu, lọc danh mục, chi tiết món/tùy chọn và triển khai đầy đủ CRUD quản trị menu (Category, Food, Food Option).
- Frontend: Kết nối menu, product detail, admin menu và admin categories với dữ liệu động thật; xử lý trạng thái giao diện và UX mượt mà.
- API: Chuyển toàn bộ các route admin menu từ 501 Not Implemented sang xử lý CRUD thực tế, chuẩn hóa định dạng JSON và kiểm tra quyền quản trị.
- Kịch bản Demo Gate 2: Xem menu → Lọc danh mục → Xem món/tùy chọn → Admin cập nhật món.

## Đã thực hiện

### Backend

- **Exceptions**:
  - `ValidationException` (HTTP 400 Bad Request): xử lý lỗi dữ liệu nhập với chi tiết từng trường (`fieldErrors`).
  - `ConflictException` (HTTP 409 Conflict): xử lý trùng lặp tên danh mục, trùng tùy chọn món hoặc xóa danh mục đang chứa món.
  - `ForbiddenException` (HTTP 403 Forbidden): bảo vệ endpoint quản trị khi tài khoản không có quyền `ADMIN` hoặc `MENU_MANAGER`.
- **DTOs (Request Models)**:
  - `CategoryRequest`: thêm và sửa danh mục (`name`, `description`).
  - `FoodRequest`: thêm và sửa món ăn (`categoryId`, `name`, `price`, `imageUrl`, `description`, `status`).
  - `FoodOptionRequest`: thêm và sửa tùy chọn món (`foodId`, `optionType`, `name`, `extraPrice`, `status`).
  - `FoodStatusUpdateRequest`: cập nhật trạng thái món (`AVAILABLE` / `UNAVAILABLE`).
- **Validation**:
  - `MenuValidator`: kiểm tra dữ liệu đầu vào cho Category, Food, Food Option (chuỗi rỗng, độ dài chuỗi, giá tiền lớn hơn 0, phụ thu không âm).
- **Service (`MenuService`)**:
  - Hoàn thiện nghiệp vụ Category: `createCategory`, `updateCategory`, `deleteCategory` (kiểm tra món trực thuộc).
  - Hoàn thiện nghiệp vụ Food: `createFood`, `updateFood`, `updateFoodStatus`, `deleteFood`, `getAllFoodsForAdmin`.
  - Hoàn thiện nghiệp vụ Food Option: `createFoodOption`, `updateFoodOption`, `deleteFoodOption`, `getOptionsByFoodId`.
- **Servlets**:
  - `MenuPageServlet`: nạp dữ liệu danh mục, danh sách món cho `/menu`, `/menu/detail`, `/admin/menu`, `/admin/categories`.
  - `AdminMenuApiServlet`: hiện thực hóa toàn bộ các method GET, POST, PUT, DELETE cho `/api/admin/categories/*`, `/api/admin/foods/*`, `/api/admin/options/*`.

### Frontend

- `frontend/WEB-INF/views/components/header.jspf`:
  - Đồng bộ navigation bar chuẩn theo thiết kế mockup (Image 2): Logo `crave.`, menu điều hướng trung tâm (`Home`, `Food`, `Quản lý món`, `Quản lý danh mục`, `Hồ sơ`, `Địa chỉ`), cụm icon tìm kiếm, giỏ hàng kèm chấm đỏ và nút `Log in`.
- `frontend/WEB-INF/views/components/footer.jspf`:
  - Footer tối màu (`#18222d`) chuẩn theo mockup (Image 2): Thương hiệu `crave. Fresh favorites, delivered.`, bản quyền `© 2026 Crave delivery` và icon mạng xã hội.
- `frontend/WEB-INF/views/menu/index.jsp`:
  - Tái thiết kế toàn diện theo chuẩn mockup (Image 1 & Image 2):
    1. **Hero Banner**: Khung bo góc lớn màu peach ấm (`#fdede5`), tiêu đề `Delicious food, delivered to you.`, thanh tìm kiếm bo tròn kèm location badge (`Downtown`) và nút `Order Now`, hình ảnh bàn tiệc ẩm thực góc chụp từ trên xuống.
    2. **Browse by craving**: Lưới các nhóm món dạng card hình ảnh (Burgers, Pizza, Sushi, Noodles, Desserts) và liên kết `See all categories`.
    3. **Find something delicious**: Bộ lọc danh mục dạng tab thanh mảnh (Popular, Main dishes, Drinks,...), lưới món ăn thẻ trắng bo tròn hiện đại, hiển thị giá màu cam nổi bật và icon trái tim yêu thích tương tác.
    4. **Promo Banner**: Banner cam san hô rực rỡ `20% OFF YOUR FIRST ORDER`, mã giảm giá `FIRSTBITE` và nút kêu gọi hành động `Order Now`.
    5. **Feature Badges**: 4 thẻ giá trị dịch vụ (Fast delivery, Fresh food, Secure payment, 24/7 support).
- `frontend/WEB-INF/views/menu/detail.jsp`:
  - Giao diện chi tiết món ăn 2 cột chuẩn thương mại điện tử, đồng bộ font Plus Jakarta Sans và bảng màu ấm.
  - Bộ chọn nhóm tùy chọn động (Kích cỡ Size, Topping, Mức đường, Mức đá) và tính năng tính tổng tiền tự động theo thời gian thực (real-time price calculation).
- `frontend/WEB-INF/views/admin/menu.jsp` & `frontend/WEB-INF/views/admin/categories.jsp`:
  - Giao diện quản trị món và danh mục đồng bộ phong cách thiết kế, hỗ trợ thống kê, bộ lọc, modal tạo/sửa và các thao tác AJAX mượt mà.
- `frontend/assets/js/menu-admin.js`:
  - Xử lý tương tác AJAX, modal, hiển thị floating toast thông báo kết quả.
- `frontend/assets/css/styles.css`:
  - Cập nhật toàn bộ Design System chuẩn xác theo mockup: bảng màu ấm (`#fdede5`, `#ef5b35`, `#fffaf5`), kiểu chữ Plus Jakarta Sans, hiệu ứng hover, thẻ card và footer tối màu.

### API

- Cung cấp và hoàn thiện các API endpoints:
  - `GET /api/admin/categories`: Danh sách toàn bộ danh mục kèm số lượng món.
  - `GET /api/admin/categories/{id}`: Chi tiết danh mục.
  - `POST /api/admin/categories`: Tạo danh mục mới (HTTP 201).
  - `PUT /api/admin/categories/{id}`: Cập nhật danh mục (HTTP 200).
  - `DELETE /api/admin/categories/{id}`: Xóa danh mục (HTTP 200 / 409 nếu có món).
  - `GET /api/admin/foods`: Danh sách món ăn quản trị (lọc theo danh mục, từ khóa, trạng thái).
  - `GET /api/admin/foods/{id}`: Chi tiết món kèm tùy chọn.
  - `POST /api/admin/foods`: Tạo món ăn mới (HTTP 201).
  - `PUT /api/admin/foods/{id}`: Cập nhật thông tin món ăn (HTTP 200).
  - `PUT /api/admin/foods/{id}/status`: Đổi trạng thái món ăn (HTTP 200).
  - `DELETE /api/admin/foods/{id}`: Xóa món ăn (HTTP 200).
  - `GET /api/admin/foods/{id}/options`: Danh sách tùy chọn của món.
  - `POST /api/admin/foods/{id}/options`: Tạo tùy chọn mới cho món (HTTP 201).
  - `PUT /api/admin/options/{id}`: Cập nhật tùy chọn (HTTP 200).
  - `DELETE /api/admin/options/{id}`: Xóa tùy chọn (HTTP 200).
- Kiểm tra phân quyền: Trả về HTTP 403 Forbidden nếu tài khoản khách hàng hoặc nhân viên không có vai trò quản lý thực đơn gọi API.

### Database

- Không thay đổi schema cơ sở dữ liệu.
- Hoàn toàn tương thích và sử dụng các bảng `category`, `food`, `food_option` trong `database/schema.sql`.

### Kiểm thử (Unit Tests)

- Bổ sung và cập nhật đầy đủ các bộ test:
  - `MenuServiceTest`: 23 tests kiểm tra nghiệp vụ đọc, tạo, sửa, xóa, validation, ràng buộc trùng lặp và tính toán cho Category, Food, Food Option.
  - `MenuRouteContractTest`: 2 tests kiểm tra đăng ký servlet route khớp với hợp đồng kiến trúc.
  - `AdminMenuApiServletTest`: 8 tests kiểm tra gọi API, request input stream, status code và JSON serialization.
  - `EntityMappingTest`: 4 tests kiểm tra JPA metadata.

## File hoặc khu vực đã thay đổi

- `backend/src/main/java/com/foodordering/api/AdminMenuApiServlet.java`
- `backend/src/main/java/com/foodordering/dto/CategoryRequest.java`
- `backend/src/main/java/com/foodordering/dto/FoodOptionRequest.java`
- `backend/src/main/java/com/foodordering/dto/FoodRequest.java`
- `backend/src/main/java/com/foodordering/dto/FoodStatusUpdateRequest.java`
- `backend/src/main/java/com/foodordering/exception/ConflictException.java`
- `backend/src/main/java/com/foodordering/exception/ForbiddenException.java`
- `backend/src/main/java/com/foodordering/exception/ValidationException.java`
- `backend/src/main/java/com/foodordering/service/MenuService.java`
- `backend/src/main/java/com/foodordering/servlet/MenuPageServlet.java`
- `backend/src/main/java/com/foodordering/validator/MenuValidator.java`
- `backend/src/test/java/com/foodordering/api/AdminMenuApiServletTest.java`
- `backend/src/test/java/com/foodordering/api/MenuRouteContractTest.java`
- `backend/src/test/java/com/foodordering/service/MenuServiceTest.java`
- `frontend/WEB-INF/views/admin/categories.jsp`
- `frontend/WEB-INF/views/admin/menu.jsp`
- `frontend/WEB-INF/views/components/header.jspf`
- `frontend/WEB-INF/views/menu/detail.jsp`
- `frontend/WEB-INF/views/menu/index.jsp`
- `frontend/assets/css/styles.css`
- `frontend/assets/js/menu-admin.js`
- `docs/reports/2026-10-10/02-nguyen-minh-huan-menu-management-day2.md`

## Cách kiểm tra

1. Chạy toàn bộ unit tests và contract tests của slice Menu:
   ```powershell
   mvn -f backend/pom.xml test "-Dtest=MenuServiceTest,MenuRouteContractTest,AdminMenuApiServletTest,EntityMappingTest"
   ```
2. Đóng gói WAR:
   ```powershell
   mvn -f backend/pom.xml package -DskipTests
   ```
3. Kiểm tra định dạng Git:
   ```powershell
   git diff --check
   ```

## Kết quả kiểm tra

- **Maven Test**: Passed toàn bộ 37/37 tests (0 failure, 0 error, 0 skipped).
- **Maven Package**: Đóng gói thành công `backend/target/crave.war`.
- **Git diff**: Không có lỗi khoảng trắng hoặc cú pháp (`git diff --check` mã thoát 0).

## Ảnh hưởng và lưu ý

- Breaking change: Không.
- Migration cần chạy: Không.
- Cấu hình cần bổ sung: Không.

## Công việc còn lại

- Sẵn sàng tích hợp luồng Ngày 3 (kết nối Giỏ hàng và Checkout với các thành viên khác).
