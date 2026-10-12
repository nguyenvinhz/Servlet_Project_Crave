# Báo cáo: Chặn voucher ngừng hoạt động ở tầng database

- Người thực hiện: Nguyễn Quang Vinh
- Ngày: 2026-10-11
- Branch: `fix/inactive-voucher-discount`
- Task/Issue: [Issue #23](https://github.com/nguyenvinhz/Servlet_Project_Crave/issues/23)
- Phạm vi báo cáo: Toàn bộ branch `fix/inactive-voucher-discount`

## Mục tiêu

`sp_calculate_discount` đang tính giảm giá cho voucher `INACTIVE` nếu vẫn còn
thời hạn và đủ giá trị đơn. API đã kiểm tra trạng thái, nhưng trigger tạo/cập nhật
đơn gọi trực tiếp procedure nên tầng database cần cùng điều kiện `ACTIVE`.

## Đã thực hiện

### Backend

- Thêm `PromotionDiscountIntegrationTest` với 21 trường hợp chạy trên MySQL thật
  qua trigger: trạng thái voucher, giảm cố định/phần trăm, làm tròn, trần giảm,
  giới hạn subtotal, thời gian và giá trị tối thiểu, đổi voucher/subtotal/thời gian.
- Kiểm tra cả `discount_amount` và `total_amount` được database sinh tự động.
- Mỗi test tạo fixture riêng trong transaction và rollback toàn bộ sau khi chạy.

### Frontend

- Không thay đổi.

### API

- Không thay đổi endpoint hoặc contract; giữ kiểm tra trạng thái ở API hiện có.

### Database

- Thêm `AND status = 'ACTIVE'` vào truy vấn promotion trong procedure.
- Thêm migration chỉ thay thế `sp_calculate_discount` cho database đã tồn tại.
- Giữ nguyên trigger: đổi voucher/subtotal/thời gian sẽ tính lại giảm giá;
  chỉ đổi trạng thái đơn vẫn giữ snapshot giảm giá trước đó.
- Áp dụng migration ở MySQL cục bộ bằng cùng chủ sở hữu routine `root@localhost`;
  đã kiểm tra và giữ quyền routine hiện có, không bổ sung quyền cho ứng dụng.

## File hoặc khu vực đã thay đổi

- `database/schema.sql`
- `database/migrations/2026-10-11-active-promotion-discount.sql`
- `backend/src/test/java/com/foodordering/repository/PromotionDiscountIntegrationTest.java`
- `README.md`
- Báo cáo này và `docs/reports/README.md`.

## Cách kiểm tra

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
mvn -f backend/pom.xml '-Dtest=PromotionDiscountIntegrationTest' test
mvn -f backend/pom.xml clean verify
```

Ngoài Maven, đã chạy smoke JDBC với fixture rollback qua trigger bằng tài khoản
ứng dụng và gọi trực tiếp procedure bằng tài khoản sở hữu routine. Tài khoản ứng
dụng hiện không có quyền `EXECUTE` trực tiếp; smoke dùng đường trigger hiện có,
giống test hồi quy. So sánh procedure trong bootstrap/migration sau khi chuẩn hóa
khoảng trắng và chạy `git diff --check`.

## Kết quả kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Hồi quy trước migration | 21 test: 17 đạt, 4 lỗi đúng issue #23 |
| Hồi quy sau migration | 21/21 đạt |
| Maven `clean verify` | 256/256 đạt, không lỗi/skip; WAR đóng gói thành công |
| Smoke bằng tài khoản ứng dụng qua trigger | 15/15 đạt |
| Smoke trực tiếp procedure/trigger bằng chủ routine | 17/17 đạt |
| Bootstrap và migration | Procedure giống nhau sau chuẩn hóa khoảng trắng |
| Rà soát độc lập | Không có lỗi chặn trong SQL, migration hoặc test |

Các fixture smoke được rollback, xác nhận không còn bản ghi thử. Bốn lỗi trước
migration là voucher cố định/phần trăm `INACTIVE` vẫn giảm, đổi subtotal sau khi
tắt voucher vẫn giảm, và thay voucher bằng mã `INACTIVE` vẫn giảm.

## Ảnh hưởng và lưu ý

- Breaking change: Voucher `INACTIVE` cho mức giảm `0` tại tầng database.
- Migration: Chạy `database/migrations/2026-10-11-active-promotion-discount.sql`
  trên database đã tồn tại bằng chủ routine. Tạm dừng ghi đơn lúc thay procedure
  qua `DROP`/`CREATE` và khôi phục đúng quyền `EXECUTE` riêng nếu đã cấp trước đó.
- Cấu hình cần bổ sung: Không có.
- Đơn cũ: Migration không sửa dữ liệu đơn; cập nhật chỉ trạng thái đơn giữ mức
  giảm đã lưu. Khi thay đầu vào giá/voucher/thời gian, trigger tính lại theo
  trạng thái promotion hiện tại.

## Công việc còn lại

- Khi triển khai sang database khác, chạy migration trước khi kiểm thử hoặc nhận
  đơn mới. MySQL cục bộ đã được cập nhật và kiểm tra.
