# Tài liệu dự án

Tài liệu được chia theo mục đích sử dụng. Xem [README của dự án](../README.md) để cài đặt, chạy ứng dụng và kiểm thử.

| Nhóm | Nội dung | Tài liệu |
|---|---|---|
| `project/` | Phân công, kế hoạch, cấu hình tích hợp và CI/CD | [Phân công nhiệm vụ 3 ngày](project/phan-cong-nhiem-vu-3-ngay.md), [Quy tắc Git/GitHub](project/quy-tac-git-github.md), [CI/CD lên EC2](project/ci-cd-ec2.md), [Thanh toán payOS](project/payos.md), [Xác thực email](project/email-verification.md) |
| `architecture/servlet-forms.md` | Hợp đồng form, Servlet và JSP đang sử dụng | [Đường dẫn và dữ liệu form](architecture/servlet-forms.md) |
| `architecture/` | Cấu trúc mã nguồn và ánh xạ sơ đồ lớp | [Sơ đồ và kiến trúc](architecture/README.md) |
| `reports/` | Báo cáo công việc và đánh giá theo chức năng | [Mục lục báo cáo](reports/README.md) |

```text
docs/
├── README.md
├── project/
├── architecture/
└── reports/
    ├── README.md
    ├── account/
    ├── cart-voucher/
    ├── menu-management/
    ├── order-payment/
    └── documentation/
```

Khi thêm tài liệu, đặt vào nhóm tương ứng và cập nhật mục lục. Báo cáo dùng tên `YYYY-MM-DD-ho-ten-thanh-vien-task.md` trong thư mục chức năng; mỗi task/branch giữ một báo cáo và cập nhật chính file đó theo [quy tắc báo cáo](project/quy-tac-git-github.md#91-vị-trí-và-tên-file).

Các hợp đồng JSON và preview không còn dùng đã được xóa. Báo cáo lịch sử có thể
nhắc đến các đường dẫn cũ; tài liệu hiện hành là hợp đồng form Servlet ở trên.
