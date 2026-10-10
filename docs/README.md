# Tài liệu dự án

Tài liệu được chia theo mục đích sử dụng. Xem [README của dự án](../README.md) để cài đặt, chạy ứng dụng và kiểm thử.

| Nhóm | Nội dung | Tài liệu |
|---|---|---|
| `project/` | Phân công, kế hoạch và cách phối hợp | [Phân công nhiệm vụ 3 ngày](project/phan-cong-nhiem-vu-3-ngay.md), [Quy tắc Git/GitHub](project/quy-tac-git-github.md) |
| `api/` | Hợp đồng API dạng OpenAPI | [Tài khoản, hồ sơ và địa chỉ](api/api-auth-profile-address.yaml), [Đơn hàng và thanh toán](api/api-order-payment.yaml) |
| `reports/` | Báo cáo công việc và đánh giá theo chức năng | [Mục lục báo cáo](reports/README.md) |

```text
docs/
├── README.md
├── api/
├── project/
└── reports/
    ├── README.md
    ├── account/
    ├── cart-voucher/
    ├── menu-management/
    ├── order-payment/
    └── documentation/
```

Khi thêm tài liệu, đặt vào nhóm tương ứng và cập nhật mục lục. Báo cáo dùng tên `YYYY-MM-DD-ho-ten-thanh-vien-task.md` trong thư mục chức năng; mỗi task/branch giữ một báo cáo và cập nhật chính file đó theo [quy tắc báo cáo](project/quy-tac-git-github.md#91-vị-trí-và-tên-file).
