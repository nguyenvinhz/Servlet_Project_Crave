# Báo cáo: Chuẩn hóa tên theo chức năng

- Người thực hiện: Nguyễn Quang Vinh
- Ngày: 2026-10-04
- Branch: feature/account-foundation
- Task/Issue: Loại tên thành viên khỏi tên branch và đường dẫn report
- Commit dự kiến: `docs(git): đặt tên branch và report theo chức năng`

## Mục tiêu

Đảm bảo tên branch, tên report và tên source chỉ mô tả chức năng hoặc phạm vi kỹ
thuật, không chứa tên thật hay tên viết tắt của người thực hiện.

## Đã thực hiện

### Backend

- Không thay đổi.

### Frontend

- Không thay đổi.

### API

- Không thay đổi.

### Database

- Không thay đổi.

## File hoặc khu vực đã thay đổi

- `docs/quy-tac-git-github.md`
- `docs/reports/2026-10-04/01-account-foundation.md`
- `docs/reports/2026-10-04/02-functional-naming.md`

## Cách kiểm tra

- Tìm các mẫu tên thành viên còn nằm trong branch/report path mẫu.
- Chạy `git diff --check`.
- Chạy `git branch --show-current`.

## Kết quả kiểm tra

- Branch hiện tại: `feature/account-foundation`.
- Không còn tên thành viên trong mẫu branch hoặc đường dẫn report.
- Không chạy lại Maven vì thay đổi chỉ liên quan tài liệu và tên Git.

## Ảnh hưởng và lưu ý

- Breaking change: quy ước đặt tên branch/report cũ được thay thế.
- Migration cần chạy: Không có.
- Cấu hình cần bổ sung: Không có.
- Giới hạn hiện tại: branch đã push trước đây cần được đổi tên thủ công nếu áp dụng quy tắc mới.

## Công việc còn lại

- Các thành viên sử dụng quy tắc tên mới cho task tiếp theo.
