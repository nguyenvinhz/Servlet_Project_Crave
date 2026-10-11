# Báo cáo: Tích hợp, kiểm thử và hoàn thiện Menu & Quản lý món Ngày 3

- Người thực hiện: Nguyễn Minh Huân
- Ngày: 2026-10-11
- Branch: feature/menu-management-day3
- Task/Issue: Ngày 3 — Tích hợp, kiểm thử và hoàn thiện theo vertical slice Menu & Quản lý món
- Phạm vi báo cáo: Toàn bộ branch `feature/menu-management-day3`

## Mục tiêu

Hoàn thiện toàn diện nhiệm vụ Ngày 3 theo kế hoạch và tiêu chuẩn nghiệm thu Gate 3 (Definition of Done):
- **Backend:** Xử lý triệt để trạng thái món không bán (`UNAVAILABLE`), kiểm tra tính hợp lệ của tùy chọn món (`ACTIVE`, đúng món, giới hạn nhóm đơn chọn), xử lý ràng buộc xóa dữ liệu an toàn (`ConflictException`), và đảm bảo tính nhất quán giữa thực đơn và giỏ hàng (Menu-Cart Consistency).
- **Frontend:** Hoàn thiện giao diện Menu (`/menu`), Chi tiết món ăn (`/menu/detail`), và Quản lý thực đơn (`/admin/menu` & `/admin/categories`). Bổ sung empty state trực quan, banner cảnh báo món tạm ngừng phục vụ, tối ưu hóa responsive di động (thanh tab danh mục vuốt ngang, lưới món co giãn linh hoạt, thanh thao tác đặt món thuận tiện trên mobile), và tính năng bật/tắt (toggle) trạng thái tùy chọn món ăn một chạm bằng AJAX.
- **API:** Mở rộng và hoàn thiện các endpoint API phục vụ kiểm tra tính khả dụng của món ăn (`GET /api/foods/{id}/validate`), kiểm tra tính nhất quán giỏ hàng (`POST /api/foods/validate-cart`), và cập nhật trạng thái tùy chọn (`PUT /api/admin/options/{id}/status`).
- **Kiểm thử:** Bổ sung đầy đủ unit tests và integration/contract tests, đạt 100% tỷ lệ vượt qua (70/70 tests thành công).

---

## Đã thực hiện

### 1. Backend

- **DTOs & Models mới:**
  - `CartConsistencyItemRequest`: Tiếp nhận thông tin món trong giỏ hàng (mã món, danh sách optionIds đã chọn, đơn giá ghi nhận) để đối chiếu với thực đơn thời gian thực.
  - `CartConsistencyItemResult`: Kết quả đối chiếu chi tiết từng món (khả dụng, options hợp lệ, giá khớp/thay đổi, đơn giá hiện tại, thông điệp hướng dẫn khách hàng).
  - `CartConsistencyReport`: Báo cáo tổng thể cho toàn bộ giỏ hàng (`allConsistent`, `totalItemsChecked`, `invalidItemsCount`, danh sách chi tiết).
  - `FoodOptionValidationResult`: Kết quả xác thực nghiệp vụ tùy chọn món ăn, tính toán phụ thu và đơn giá thực tế từ phía máy chủ.
  - `FoodOptionStatusUpdateRequest`: Hỗ trợ cập nhật nhanh trạng thái tùy chọn (`ACTIVE` / `INACTIVE`).
- **Nghiệp vụ Service (`MenuService`):**
  - `validateFoodAvailable(String foodId)`: Xác thực món ăn tồn tại và đang trong trạng thái `AVAILABLE`. Ném `ValidationException` kèm thông báo rõ ràng nếu món đã ngưng phục vụ (`UNAVAILABLE`).
  - `validateFoodOptions(String foodId, List<String> optionIds)`: Kiểm tra từng tùy chọn phải tồn tại, thuộc về chính món ăn đó, và đang ở trạng thái `ACTIVE`. Đồng thời kiểm tra xung đột nhóm: giới hạn tối đa 1 kích cỡ (`SIZE`), 1 mức đường (`SUGAR_LEVEL`), 1 mức đá (`ICE_LEVEL`). Tính toán đơn giá máy chủ không phụ thuộc client.
  - `updateFoodOptionStatus(String optionId, OptionStatus newStatus)`: Cập nhật nhanh trạng thái kích hoạt/vô hiệu của một tùy chọn món.
  - `checkMenuCartConsistency(List<CartConsistencyItemRequest> items)`: Kiểm tra tính nhất quán toàn diện giữa giỏ hàng và menu: phát hiện món bị xóa/hết hàng, options bị vô hiệu hoặc xóa, giá món bị thay đổi so với thời điểm thêm vào giỏ.
  - `deleteFood(String id)` & `deleteFoodOption(String optionId)`: Xử lý ngoại lệ ràng buộc toàn vẹn khóa ngoại (khi món/option đã có trong giỏ hàng hoặc đơn hàng), ném `ConflictException` (HTTP 409) hướng dẫn người quản trị chuyển sang trạng thái tạm khóa thay vì để lỗi máy chủ sập unhandled.

### 2. Frontend & UX/UI

- **Trang Thực đơn (`frontend/WEB-INF/views/menu/index.jsp`):**
  - **Empty State**: Bổ sung khối Empty State với minh họa đĩa ăn, tiêu đề và mô tả tiếng Việt rõ ràng, kèm nút "Xem tất cả món ăn" để người dùng dễ dàng khôi phục bộ lọc khi tìm kiếm không ra kết quả.
  - **Card Badge**: Hiển thị nhãn đỏ `TẠM HẾT` trên các món có trạng thái `UNAVAILABLE`, phủ mờ nhẹ thẻ món để khách hàng nhận biết ngay trước khi nhấp vào.
  - **Responsive Mobile**: Thanh cuộn ngang mượt mà cho tabs danh mục trên điện thoại (`overflow-x: auto; scroll-snap-type: x mandatory`), lưới món ăn co giãn thông minh (1 cột trên màn hình nhỏ < 560px, 2 cột trên mobile trung bình, 3 cột trên tablet, 4 cột trên desktop).
- **Trang Chi tiết món (`frontend/WEB-INF/views/menu/detail.jsp`):**
  - **Trạng thái món không bán**: Khi món có trạng thái `UNAVAILABLE`, hiển thị banner thông báo màu cam/đỏ nổi bật: *"⚠️ Món ăn này hiện đang tạm ngưng phục vụ. Món này hiện đang tạm hết nguyên liệu hoặc đã ngưng bán hôm nay..."*. Vô hiệu hóa toàn bộ tùy chọn và nút đặt hàng ("Món hiện tạm hết").
  - **Empty / 404 State**: Xử lý trường hợp không tìm thấy món ăn với giao diện thông báo trực quan và nút dẫn quay lại thực đơn.
  - **Responsive Layout**: Chuyển đổi linh hoạt từ layout 2 cột trên desktop sang 1 cột trên mobile; tối ưu hóa thanh điều khiển số lượng và nút bấm giỏ hàng thuận tiện cho thao tác một tay trên điện thoại.
- **Trang Quản trị (`frontend/WEB-INF/views/admin/menu.jsp` & `frontend/assets/js/menu-admin.js`):**
  - **Bật/Tắt trạng thái tùy chọn tức thì**: Thêm nút toggle trạng thái tùy chọn (`● Khả dụng` / `○ Tạm khóa`) ngay trong modal quản lý tùy chọn của món ăn. Cập nhật qua AJAX không cần tải lại trang.
  - **Thông báo & Xử lý lỗi**: Hiển thị toast thông báo kết quả thân thiện (thành công / cảnh báo lỗi), ngăn ngừa việc xóa nhầm hoặc lỗi ngoại lệ.
- **Design System (`frontend/assets/css/styles.css`):**
  - Bổ sung style cho `.unavailable-notice-banner`, `.card-status-badge`, `.food-card-unavailable`, `.empty-state`, `.empty-icon-art`, cùng các breakpoints responsive hoàn thiện.

### 3. API & Endpoints

- **`FoodApiServlet`:**
  - `GET /api/foods`: Lấy danh sách món khả dụng (hỗ trợ lọc danh mục & từ khóa).
  - `GET /api/foods/{id}`: Lấy chi tiết món ăn (kèm danh sách tùy chọn đang hoạt động).
  - `GET /api/foods/{id}/validate`: Kiểm tra nhanh món ăn có khả dụng để đặt hàng hay không.
  - `POST /api/foods/validate-cart`: Endpoint kiểm tra tính nhất quán menu-cart cho danh sách món trong giỏ hàng (hỗ trợ cả JSON array lẫn object wrapper).
- **`AdminMenuApiServlet`:**
  - `PUT /api/admin/options/{id}/status`: Đổi trạng thái tùy chọn (ACTIVE / INACTIVE).
  - `PUT /api/admin/foods/{id}/options/{optionId}/status`: Hỗ trợ đường dẫn lồng nhau theo chuẩn RESTful.
  - Chuẩn hóa mã phản hồi lỗi: HTTP 400 (Validation), HTTP 401 (Unauthorized), HTTP 403 (Forbidden), HTTP 404 (Not Found), HTTP 409 (Conflict).

---

## Kiểm thử (Tests)

Đã bổ sung và mở rộng đầy đủ các bộ test tự động:
- **`MenuServiceTest`**: 44 tests (bổ sung 21 tests kiểm tra xác thực món ăn khả dụng, kiểm tra options hợp lệ, phát hiện xung đột loại option, đổi trạng thái option, đối chiếu tính nhất quán menu-cart và xử lý ràng buộc xóa).
- **`AdminMenuApiServletTest`**: 18 tests (bổ sung tests cập nhật trạng thái option qua các route status và kiểm tra chặn quyền khách hàng truy cập API quản trị).
- **`FoodApiServletTest`**: 6 tests (kiểm tra lấy menu, lọc từ khóa/danh mục, lấy chi tiết, endpoint xác thực món và endpoint POST kiểm tra tính nhất quán giỏ hàng).
- **`MenuRouteContractTest`**: 2 tests (kiểm tra toàn vẹn hợp đồng đăng ký route Servlet).

**Tổng số test chạy thành công:** 70 / 70 tests (0 failure, 0 error, 0 skipped).  
**Đóng gói WAR:** `mvn -f backend/pom.xml package -DskipTests` -> `BUILD SUCCESS` (`backend/target/crave.war`).

---

## File đã thay đổi và bổ sung

1. **DTOs mới:**
   - `backend/src/main/java/com/foodordering/dto/CartConsistencyItemRequest.java`
   - `backend/src/main/java/com/foodordering/dto/CartConsistencyItemResult.java`
   - `backend/src/main/java/com/foodordering/dto/CartConsistencyReport.java`
   - `backend/src/main/java/com/foodordering/dto/FoodOptionValidationResult.java`
   - `backend/src/main/java/com/foodordering/dto/FoodOptionStatusUpdateRequest.java`
2. **Backend Services & Servlets:**
   - `backend/src/main/java/com/foodordering/service/MenuService.java`
   - `backend/src/main/java/com/foodordering/api/AdminMenuApiServlet.java`
   - `backend/src/main/java/com/foodordering/api/FoodApiServlet.java`
3. **Frontend Views & Assets:**
   - `frontend/WEB-INF/views/menu/index.jsp`
   - `frontend/WEB-INF/views/menu/detail.jsp`
   - `frontend/WEB-INF/views/admin/menu.jsp`
   - `frontend/assets/js/menu-admin.js`
   - `frontend/assets/css/styles.css`
4. **Unit Tests:**
   - `backend/src/test/java/com/foodordering/service/MenuServiceTest.java`
   - `backend/src/test/java/com/foodordering/api/AdminMenuApiServletTest.java`
   - `backend/src/test/java/com/foodordering/api/FoodApiServletTest.java`
5. **Tài liệu & Báo cáo:**
   - `docs/reports/menu-management/2026-10-11-nguyen-minh-huan-menu-management-day3.md`
   - `docs/reports/README.md`
