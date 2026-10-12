# Kiểm tra migration

`baseline-before-active-promotion.sql` là bản nguyên vẹn của `database/schema.sql`
tại commit `4a9538b0511b57ee31bd50cb5fce26670fb2a2ac`, trước bản sửa voucher
INACTIVE. Giữ cố định fixture này để kiểm tra nâng cấp từ schema cũ; không tạo
lại nó từ `database/schema.sql` hiện tại.

`migration-fixtures.sql` tạo một khách hàng, hai voucher và hai đơn có dữ liệu
được xác định trước. `scripts/ci/test-migrations.sh` kiểm tra:

- Schema cũ thực sự giảm tiền cho voucher INACTIVE.
- Migration không thay đổi dữ liệu đang có: so sánh bản dump dữ liệu trước/sau.
- Procedure sau migration trả 0 cho INACTIVE và giữ giảm giá cho ACTIVE.
- Đổi trạng thái đơn cũ giữ nguyên snapshot giảm giá.
- Đơn mới qua trigger áp dụng đúng quy tắc sau migration.

Mỗi migration mới cần fixture schema trước migration và các assertion phù hợp.
Lượt kiểm tra hiện tại dành riêng cho
`2026-10-11-active-promotion-discount.sql`, không đại diện cho mọi migration
có thể được thêm trong tương lai.

Các script thử nghiệm xóa/tạo database `crave` và chỉ chạy khi
`CI_DISPOSABLE_DATABASE=true`. Dùng MySQL tạm riêng; không trỏ vào database đang
có dữ liệu cần giữ.
