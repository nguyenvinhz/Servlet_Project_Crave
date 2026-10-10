# Báo cáo: Xây dựng Backend và API Giỏ hàng & Khuyến mãi (Cart & Promotion)

- Người thực hiện: Ung Văn Trí (tri)
- Ngày: 2026-10-03
- Branch: feature/tri/cart-voucher
- Task/Issue: Backend vertical slice Giỏ hàng (Cart) và Khuyến mãi (Promotion)
- Commit dự kiến: feat(cart): hoàn thiện backend và api giỏ hàng cùng khuyến mãi

## Mục tiêu

Xây dựng toàn bộ tầng backend (Entity, DTO, Repository, Service, Servlet API, Validator, Exception) cho phân hệ Giỏ hàng (Cart, Cart Item, Cart Item Option) và Khuyến mãi (Promotion, Voucher validation) thuộc phạm vi phân công của Ung Văn Trí. Thiết lập chuẩn định dạng JSON ApiResponse và ErrorCode dùng chung cho toàn bộ API của dự án. Đảm bảo nguyên tắc bảo mật: toàn bộ việc tính toán giá tiền, phụ phí options, subtotal và mức giảm giá đều thực hiện tại máy chủ, không tin cậy dữ liệu do client gửi lên.

## Đã thực hiện

### Backend

- **Entity & Enums**:
  - `DiscountType`: PERCENT, FIXED_AMOUNT khớp ENUM database.
  - `PromotionStatus`: ACTIVE, INACTIVE.
  - `FoodOptionType`: SIZE, TOPPING, SUGAR_LEVEL, ICE_LEVEL, OTHER.
  - `ErrorCode`: Danh mục mã lỗi hệ thống và mã lỗi nghiệp vụ cho Cart, Promotion.
  - `Cart`, `CartItem`, `CartItemOption`, `Promotion`: Ánh xạ cấu trúc các bảng `cart`, `cart_item`, `cart_item_option`, `promotion`.
- **DTO**:
  - `ApiResponse<T>`: Định dạng JSON phản hồi chuẩn (success, message, data, errorCode, timestamp).
  - `CartDto`, `CartItemDto`, `CartItemOptionDto`: Mô hình giỏ hàng và chi tiết món với tính toán tự động phụ phí và thành tiền dòng.
  - `AddToCartRequest`, `UpdateCartItemRequest`: Request nhận dữ liệu thêm/sửa món trong giỏ.
  - `PromotionDto`, `ValidatePromotionRequest`, `PromotionValidationResultDto`: Mô hình tra cứu, xác thực và trả kết quả tính giảm giá voucher.
- **Exceptions & Validators**:
  - `ApiException`, `BadRequestException`, `ResourceNotFoundException`, `PromotionValidationException`.
  - `CartValidator`: Kiểm tra tính hợp lệ dữ liệu thêm, sửa, xóa giỏ hàng.
  - `PromotionValidator`: Kiểm tra mã code, hạn sử dụng, trạng thái và giá trị đơn hàng tối thiểu.
- **Repository (JDBC)**:
  - `CartRepository`: Tìm giỏ hàng theo customer, tạo mới giỏ hàng, đọc subtotal từ view `v_cart_summary`.
  - `CartItemRepository`: Lấy danh sách món + options, kiểm tra trạng thái món `AVAILABLE`, xác thực option hợp lệ `ACTIVE`, nhận diện món trùng (cùng món và cùng bộ option) để cộng dồn số lượng, thêm mới món với options trong transaction, cập nhật số lượng, xóa món và dọn sạch giỏ.
  - `PromotionRepository`: Tìm kiếm theo mã code, tìm theo ID, liệt kê danh sách khuyến mãi đang hiệu lực, gọi Stored Procedure `sp_calculate_discount`.
- **Service**:
  - `CartService`: Quản lý nghiệp vụ giỏ hàng, đảm bảo tính toán giá an toàn phía server.
  - `PromotionService`: Xác thực voucher, kiểm tra điều kiện áp dụng, thuật toán tính giảm giá chính xác theo % có chặn trần tối đa hoặc số tiền cố định.
- **Test**:
  - `CartPromotionServiceTest`: Bộ kiểm thử đơn vị bao phủ 7 ca kiểm thử cốt lõi (tính đơn giá/thành tiền có options, giảm giá %, chặn trần tối đa, giảm tiền cố định, chặn đơn chưa đạt mức tối thiểu, chặn mã hết hạn, định dạng JSON ApiResponse).

### Frontend

- Không thay đổi (Theo yêu cầu phân công chỉ thực hiện phần Backend).

### API

- `CartApiServlet` (`/api/cart`, `/api/cart/*`):
  - `GET /api/cart`: Lấy thông tin giỏ hàng hiện tại của khách hàng.
  - `POST /api/cart` / `POST /api/cart/items`: Thêm món vào giỏ (foodId, quantity, note, optionIds).
  - `PUT /api/cart/items` / `POST /api/cart/items/update`: Cập nhật số lượng món (xóa nếu quantity = 0).
  - `DELETE /api/cart/items` / `POST /api/cart/items/remove`: Xóa một món khỏi giỏ hàng.
  - `DELETE /api/cart` / `POST /api/cart/clear`: Xóa toàn bộ món trong giỏ hàng.
  - Tự động nhận diện `customerId` qua session hoặc header `X-Customer-Id` / query param `customerId`.
- `PromotionApiServlet` (`/api/promotions`, `/api/promotions/*`):
  - `GET /api/promotions/active`: Danh sách mã khuyến mãi đang có hiệu lực.
  - `GET /api/promotions?code=...`: Tra cứu thông tin chi tiết một mã khuyến mãi.
  - `POST /api/promotions/validate`: Kiểm tra voucher theo mã và subtotal (hoặc tự lấy từ giỏ hàng hiện tại).

### Database

- Tận dụng đầy đủ cấu trúc database từ `database/schema.sql`: các bảng `cart`, `cart_item`, `cart_item_option`, `promotion`, các view `v_cart_item_total`, `v_cart_summary` và stored procedure `sp_calculate_discount`.
- Cung cấp `DatabaseConfig` quản lý kết nối JDBC MySQL linh hoạt qua biến môi trường hoặc cấu hình mặc định.

## File hoặc khu vực đã thay đổi

- `backend/src/main/java/com/foodordering/enums/DiscountType.java`
- `backend/src/main/java/com/foodordering/enums/PromotionStatus.java`
- `backend/src/main/java/com/foodordering/enums/FoodOptionType.java`
- `backend/src/main/java/com/foodordering/enums/ErrorCode.java`
- `backend/src/main/java/com/foodordering/dto/ApiResponse.java`
- `backend/src/main/java/com/foodordering/dto/CartItemOptionDto.java`
- `backend/src/main/java/com/foodordering/dto/CartItemDto.java`
- `backend/src/main/java/com/foodordering/dto/CartDto.java`
- `backend/src/main/java/com/foodordering/dto/AddToCartRequest.java`
- `backend/src/main/java/com/foodordering/dto/UpdateCartItemRequest.java`
- `backend/src/main/java/com/foodordering/dto/PromotionDto.java`
- `backend/src/main/java/com/foodordering/dto/ValidatePromotionRequest.java`
- `backend/src/main/java/com/foodordering/dto/PromotionValidationResultDto.java`
- `backend/src/main/java/com/foodordering/entity/Cart.java`
- `backend/src/main/java/com/foodordering/entity/CartItem.java`
- `backend/src/main/java/com/foodordering/entity/CartItemOption.java`
- `backend/src/main/java/com/foodordering/entity/Promotion.java`
- `backend/src/main/java/com/foodordering/exception/ApiException.java`
- `backend/src/main/java/com/foodordering/exception/ResourceNotFoundException.java`
- `backend/src/main/java/com/foodordering/exception/BadRequestException.java`
- `backend/src/main/java/com/foodordering/exception/PromotionValidationException.java`
- `backend/src/main/java/com/foodordering/config/DatabaseConfig.java`
- `backend/src/main/java/com/foodordering/utils/IdGenerator.java`
- `backend/src/main/java/com/foodordering/utils/JsonUtils.java`
- `backend/src/main/java/com/foodordering/validator/CartValidator.java`
- `backend/src/main/java/com/foodordering/validator/PromotionValidator.java`
- `backend/src/main/java/com/foodordering/repository/CartRepository.java`
- `backend/src/main/java/com/foodordering/repository/CartItemRepository.java`
- `backend/src/main/java/com/foodordering/repository/PromotionRepository.java`
- `backend/src/main/java/com/foodordering/service/CartService.java`
- `backend/src/main/java/com/foodordering/service/PromotionService.java`
- `backend/src/main/java/com/foodordering/api/CartApiServlet.java`
- `backend/src/main/java/com/foodordering/api/PromotionApiServlet.java`
- `backend/src/test/java/com/foodordering/CartPromotionServiceTest.java`
- `docs/reports/cart-voucher/2026-10-03-ung-van-tri-cart-promotion-backend.md`

## Cách kiểm tra

- Biên dịch toàn bộ source code với `javac` và thư viện servlet container Tomcat.
- Chạy kiểm thử đơn vị độc lập qua lệnh:
  `java -ea com.foodordering.CartPromotionServiceTest`
- Các ca kiểm thử bao gồm:
  1. Kiểm tra tính đơn giá (basePrice + option extraPrice) và thành tiền dòng (unitPrice * quantity) trên CartItemDto và subtotal trên CartDto.
  2. Kiểm tra tính toán giảm giá PERCENT.
  3. Kiểm tra tính toán giảm giá PERCENT có áp trần maximumDiscount.
  4. Kiểm tra tính toán giảm giá FIXED_AMOUNT.
  5. Kiểm tra validation khi đơn hàng chưa đạt minimumOrderValue.
  6. Kiểm tra validation khi voucher đã hết hạn hoặc chưa bắt đầu.
  7. Kiểm tra sinh chuỗi JSON chuẩn ApiResponse tuân thủ hợp đồng nhóm.

## Kết quả kiểm tra

- Biên dịch: Passed (0 lỗi, 0 cảnh báo).
- Unit Tests: Passed (7/7 ca kiểm thử thành công 100%).

## Ảnh hưởng và lưu ý

- Breaking change: Không có.
- Migration cần chạy: Sử dụng schema và seed data đã có tại `database/schema.sql` và `database/seed.sql`.
- Cấu hình cần bổ sung: Cấu hình biến môi trường kết nối MySQL nếu thay đổi cổng/tài khoản mặc định (DB_URL, DB_USER, DB_PASSWORD).
- Giới hạn hiện tại: Các endpoint servlet cần container Tomcat 9 (hoặc plugin Tomcat) để tiếp nhận HTTP request thực tế qua trình duyệt.

## Công việc còn lại

- Phối hợp với Nguyễn Quang Vinh khi hoàn thiện module Auth để đồng bộ `customerId` từ session đăng nhập thật.
- Phối hợp với Nguyễn Đức Phát khi đặt hàng (Checkout) để chuyển giao subtotal và thông tin voucher đã áp dụng cho đơn hàng.
