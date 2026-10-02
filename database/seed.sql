USE crave;
SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO category (category_id, name, description) VALUES
('DM01', 'Món chính', 'Các món cơm, mì, bún...'),
('DM02', 'Nước uống', 'Trà sữa, nước ép, cà phê...'),
('DM03', 'Tráng miệng', 'Bánh ngọt, chè, kem...'),
('DM04', 'Ăn vặt', 'Khoai tây chiên, gà rán, xúc xích nướng...'),
('DM05', 'Lẩu - Nướng', 'Các món lẩu, nướng phục vụ nhóm đông người'),
('DM06', 'Đồ chay', 'Món ăn chay thanh đạm, tốt cho sức khỏe');

INSERT INTO food
    (food_id, category_id, name, price, image_url, description)
VALUES
('MA01', 'DM01', 'Cơm gà xối mỡ', 45000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789654803/2023_12_6_638374928096209198_com-ga-xoi-mo-bao-nhieu-calo.webp', 'Cơm gà giòn, nước mắm chua ngọt'),
('MA02', 'DM02', 'Trà sữa trân châu', 35000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789655114/cach-lam-tra-sua-chan-trau-thom-ngon-tai-nha.jpg', 'Trà sữa truyền thống'),
('MA03', 'DM03', 'Bánh flan', 15000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789655151/cach-lam-banh-flan-thumbnail.jpg', 'Bánh flan caramel'),
('MA04', 'DM01', 'Phở bò tái', 50000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652157/crave/MA04_pho_bo_tai.jpg', 'Phở bò truyền thống, nước dùng ninh xương 8 tiếng'),
('MA05', 'DM01', 'Bún chả Hà Nội', 48000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657008/2024_1_12_638406880045931692_cach-lam-bun-cha-ha-noi-0.webp', 'Chả nướng than hoa ăn kèm bún và nước chấm'),
('MA06', 'DM01', 'Mì xào hải sản', 55000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657194/OIP.webp', 'Mì xào giòn cùng tôm, mực, rau củ'),
('MA07', 'DM01', 'Cơm tấm sườn bì chả', 42000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789656913/com-tam-suon-bi-cha-chay-1_99356b3b594740f793b4d570925b0572.jpg', 'Cơm tấm sườn nướng, bì, chả trứng'),
('MA08', 'DM01', 'Bún bò Huế', 47000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789655329/nau-bun-bo-hue-chuan-vi-tai-nha-voi-cot-co-dac-quoc-viet-foods_59b7ba1543004e67967af718d8afc32b.webp', 'Đậm vị cay nồng đặc trưng xứ Huế'),
('MA09', 'DM02', 'Cà phê sữa đá', 25000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657259/OIP.webp', 'Cà phê phin truyền thống pha sữa đặc'),
('MA10', 'DM02', 'Nước ép cam', 30000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652163/crave/MA10_nuoc_ep_cam.jpg', 'Cam tươi ép nguyên chất, không đường hóa học'),
('MA11', 'DM02', 'Sinh tố bơ', 32000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657333/OIP.webp', 'Sinh tố bơ sáp béo ngậy'),
('MA12', 'DM02', 'Trà đào cam sả', 38000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652165/crave/MA12_tra_dao_cam_sa.jpg', 'Trà đào thơm mát kèm cam và sả'),
('MA13', 'DM02', 'Nước chanh muối', 20000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652166/crave/MA13_nuoc_chanh_muoi.jpg', 'Giải khát chua nhẹ, thanh lọc'),
('MA14', 'DM03', 'Chè khúc bạch', 25000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789655493/che-khuc-bach-003-20220606.jpg', 'Khúc bạch, hạnh nhân, nhãn nhục'),
('MA15', 'DM03', 'Kem dừa', 20000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652169/crave/MA15_kem_dua.jpg', 'Kem vị dừa mát lạnh phục vụ trong trái dừa'),
('MA16', 'DM03', 'Bánh su kem', 12000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652170/crave/MA16_banh_su_kem.jpg', 'Vỏ bánh giòn, nhân kem trứng béo mịn'),
('MA17', 'DM04', 'Khoai tây chiên', 28000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652171/crave/MA17_khoai_tay_chien.jpg', 'Khoai tây chiên giòn ăn kèm tương ớt/mayonnaise'),
('MA18', 'DM04', 'Gà rán giòn', 45000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652172/crave/MA18_ga_ran_gion.jpg', 'Gà rán tẩm bột giòn rụm, 2 miếng'),
('MA19', 'DM04', 'Xúc xích nướng', 20000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657405/OIP.webp', 'Xúc xích nướng than, 3 cây'),
('MA20', 'DM05', 'Lẩu thái hải sản', 180000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657457/202311280950003415.webp', 'Lẩu chua cay hải sản, phục vụ 2-3 người'),
('MA21', 'DM05', 'Nướng BBQ thập cẩm', 220000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789652174/crave/MA21_nuong_bbq_thap_cam.jpg', 'Set nướng thịt bò, heo, gà, hải sản'),
('MA22', 'DM06', 'Đậu hũ sốt cà', 30000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657511/OIP.webp', 'Đậu hũ chiên sốt cà chua kiểu chay'),
('MA23', 'DM06', 'Rau củ xào chay', 28000, 'https://res.cloudinary.com/mkx3r87x/image/upload/v1789657565/OIP.webp', 'Rau củ theo mùa xào tỏi kiểu chay');

INSERT INTO food_option
    (option_id, food_id, option_type, name, extra_price)
VALUES
('TC01', 'MA02', 'SIZE',        'Size L',             5000),
('TC02', 'MA02', 'SUGAR_LEVEL', '50% đường',             0),
('TC03', 'MA01', 'TOPPING',     'Thêm trứng ốp la',   8000),
('TC04', 'MA04', 'SIZE',        'Tô lớn',            10000),
('TC05', 'MA04', 'TOPPING',     'Thêm bò viên',      10000),
('TC06', 'MA09', 'ICE_LEVEL',   'Ít đá',                 0),
('TC07', 'MA09', 'SIZE',        'Size L',             5000),
('TC08', 'MA10', 'SIZE',        'Size L',             5000),
('TC09', 'MA11', 'TOPPING',     'Thêm sữa đặc',       3000),
('TC10', 'MA12', 'SIZE',        'Size L',             5000),
('TC11', 'MA18', 'OTHER',       'Sốt phô mai',        5000),
('TC12', 'MA20', 'TOPPING',     'Thêm hải sản',      50000);

-- password_hash values are development placeholders; replace them with real
-- BCrypt/Argon2 hashes before testing authentication.
INSERT INTO user_account
    (user_id, account_type, full_name, email, phone, password_hash)
VALUES
('NV01', 'EMPLOYEE', 'Nguyễn Văn Quản Trị', 'admin@shop.vn',              '0900000001', 'REPLACE_WITH_PASSWORD_HASH'),
('NV02', 'EMPLOYEE', 'Lê Thị Quản Món',      'lethiquanmon@shop.vn',       '0900000010', 'REPLACE_WITH_PASSWORD_HASH'),
('NV03', 'EMPLOYEE', 'Phạm Văn Xử Lý',       'phamvanxuly@shop.vn',        '0900000011', 'REPLACE_WITH_PASSWORD_HASH'),
('NV04', 'EMPLOYEE', 'Hoàng Thị Xử Lý Hai',  'hoangxulyhai@shop.vn',       '0900000012', 'REPLACE_WITH_PASSWORD_HASH'),
('NV05', 'EMPLOYEE', 'Đỗ Văn Khuyến Mãi',    'dovankm@shop.vn',            '0900000013', 'REPLACE_WITH_PASSWORD_HASH'),
('NV06', 'EMPLOYEE', 'Vũ Thị Nhân Sự',       'vuthins@shop.vn',            '0900000014', 'REPLACE_WITH_PASSWORD_HASH'),
('NV07', 'EMPLOYEE', 'Ngô Văn Giao Hàng',    'ngovangiao@shop.vn',         '0900000015', 'REPLACE_WITH_PASSWORD_HASH'),
('NV08', 'EMPLOYEE', 'Bùi Thị Quản Món Hai', 'buiquanmonhai@shop.vn',      '0900000016', 'REPLACE_WITH_PASSWORD_HASH'),
('KH01', 'CUSTOMER', 'Trần Thị Khách',        'khachhang@gmail.com',        '0900000002', 'REPLACE_WITH_PASSWORD_HASH'),
('KH02', 'CUSTOMER', 'Nguyễn Văn An',         'an.nguyen@gmail.com',        '0911111111', 'REPLACE_WITH_PASSWORD_HASH'),
('KH03', 'CUSTOMER', 'Trần Thị Bình',         'binh.tran@gmail.com',        '0911111112', 'REPLACE_WITH_PASSWORD_HASH'),
('KH04', 'CUSTOMER', 'Lê Văn Cường',          'cuong.le@gmail.com',         '0911111113', 'REPLACE_WITH_PASSWORD_HASH'),
('KH05', 'CUSTOMER', 'Phạm Thị Dung',         'dung.pham@gmail.com',        '0911111114', 'REPLACE_WITH_PASSWORD_HASH'),
('KH06', 'CUSTOMER', 'Hoàng Văn Em',          'em.hoang@gmail.com',         '0911111115', 'REPLACE_WITH_PASSWORD_HASH'),
('KH07', 'CUSTOMER', 'Đỗ Thị Phương',         'phuong.do@gmail.com',        '0911111116', 'REPLACE_WITH_PASSWORD_HASH'),
('KH08', 'CUSTOMER', 'Vũ Văn Giang',          'giang.vu@gmail.com',         '0911111117', 'REPLACE_WITH_PASSWORD_HASH'),
('KH09', 'CUSTOMER', 'Ngô Thị Hoa',           'hoa.ngo@gmail.com',          '0911111118', 'REPLACE_WITH_PASSWORD_HASH'),
('KH10', 'CUSTOMER', 'Bùi Văn Inh',           'inh.bui@gmail.com',          '0911111119', 'REPLACE_WITH_PASSWORD_HASH'),
('KH11', 'CUSTOMER', 'Đặng Thị Kim',          'kim.dang@gmail.com',         '0911111120', 'REPLACE_WITH_PASSWORD_HASH'),
('KH12', 'CUSTOMER', 'Trịnh Văn Long',        'long.trinh@gmail.com',       '0911111121', 'REPLACE_WITH_PASSWORD_HASH'),
('KH13', 'CUSTOMER', 'Lý Thị Mai',            'mai.ly@gmail.com',           '0911111122', 'REPLACE_WITH_PASSWORD_HASH'),
('KH14', 'CUSTOMER', 'Phan Văn Nam',          'nam.phan@gmail.com',         '0911111123', 'REPLACE_WITH_PASSWORD_HASH'),
('KH15', 'CUSTOMER', 'Tô Thị Oanh',           'oanh.to@gmail.com',          '0911111124', 'REPLACE_WITH_PASSWORD_HASH');

INSERT INTO employee (employee_id, role, address, hire_date) VALUES
('NV01', 'ADMIN',             '10 Lê Lợi, Q.1, TP.HCM',                         '2025-01-01'),
('NV02', 'MENU_MANAGER',      '20 Lê Lợi, Q.1, TP.HCM',                         '2025-02-15'),
('NV03', 'ORDER_STAFF',       '15 Cách Mạng Tháng 8, Q.3, TP.HCM',              '2025-03-01'),
('NV04', 'ORDER_STAFF',       '88 Nguyễn Thị Minh Khai, Q.3, TP.HCM',           '2025-04-10'),
('NV05', 'PROMOTION_MANAGER', '5 Điện Biên Phủ, Bình Thạnh, TP.HCM',            '2025-05-20'),
('NV06', 'HR_MANAGER',        '12 Phan Xích Long, Phú Nhuận, TP.HCM',           '2025-06-01'),
('NV07', 'ORDER_STAFF',       '30 Trường Chinh, Tân Bình, TP.HCM',              '2025-07-18'),
('NV08', 'MENU_MANAGER',      '9 Hoàng Văn Thụ, Tân Bình, TP.HCM',              '2025-08-05');

INSERT INTO customer (customer_id, registered_at) VALUES
('KH01', '2026-08-01 08:00:00'),
('KH02', '2026-08-02 08:00:00'),
('KH03', '2026-08-03 08:00:00'),
('KH04', '2026-08-04 08:00:00'),
('KH05', '2026-08-05 08:00:00'),
('KH06', '2026-08-06 08:00:00'),
('KH07', '2026-08-07 08:00:00'),
('KH08', '2026-08-08 08:00:00'),
('KH09', '2026-08-09 08:00:00'),
('KH10', '2026-08-10 08:00:00'),
('KH11', '2026-08-11 08:00:00'),
('KH12', '2026-08-12 08:00:00'),
('KH13', '2026-08-13 08:00:00'),
('KH14', '2026-08-14 08:00:00'),
('KH15', '2026-08-15 08:00:00');

INSERT INTO delivery_address
    (address_id, customer_id, address_line, note, is_default)
VALUES
('DC01', 'KH01', '12 Nguyễn Trãi, Q.1, TP.HCM', 'Nhà mặt tiền, cổng xanh', 1),
('DC02', 'KH01', 'Công ty ABC, 88 Lê Duẩn, Q.1, TP.HCM', 'Giao tại quầy lễ tân tầng 1', 0),
('DC03', 'KH02', '25 Lý Tự Trọng, Q.1, TP.HCM', 'Gọi trước khi đến', 1),
('DC04', 'KH03', '40 Nguyễn Huệ, Q.1, TP.HCM', NULL, 1),
('DC05', 'KH04', '12 Võ Văn Tần, Q.3, TP.HCM', 'Chung cư, tầng 5', 1),
('DC06', 'KH06', '56 Xô Viết Nghệ Tĩnh, Bình Thạnh, TP.HCM', NULL, 1),
('DC07', 'KH07', '18 Phan Đăng Lưu, Phú Nhuận, TP.HCM', 'Giao giờ hành chính', 1),
('DC08', 'KH09', '3 Trường Sơn, Tân Bình, TP.HCM', NULL, 1),
('DC09', 'KH10', '21 Nguyễn Oanh, Gò Vấp, TP.HCM', 'Hẻm nhỏ, gọi điện trước', 1),
('DC10', 'KH12', '8 Kha Vạn Cân, Thủ Đức, TP.HCM', NULL, 1);

INSERT INTO promotion
    (promotion_id, code, name, discount_type, discount_value,
     minimum_order_value, maximum_discount, start_at, end_at, status)
VALUES
('KM01', 'WELCOME10', 'Giảm 10% toàn đơn',             'PERCENT',      10,      0,  50000, '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE'),
('KM02', 'SAVE20K',   'Giảm 20K cho đơn từ 100K',      'FIXED_AMOUNT', 20000, 100000, NULL, '2026-09-01 00:00:00', '2026-10-31 23:59:59', 'ACTIVE'),
('KM03', 'AUTUMN15',  'Giảm 15% chương trình mùa thu', 'PERCENT',      15,      0,  30000, '2026-09-01 00:00:00', '2027-03-31 23:59:59', 'ACTIVE'),
('KM04', 'SAVE50K',   'Giảm 50K cho đơn từ 300K',      'FIXED_AMOUNT', 50000, 300000, NULL, '2026-10-01 00:00:00', '2026-12-31 23:59:59', 'ACTIVE'),
('KM05', 'OPENING30', 'Mừng khai trương giảm 30%',     'PERCENT',      30,      0, 100000, '2026-01-01 00:00:00', '2026-09-30 23:59:59', 'INACTIVE'),
('KM06', 'EVERYDAY5', 'Giảm 5% mọi đơn hàng',          'PERCENT',       5,      0,  20000, '2026-09-01 00:00:00', '2027-12-31 23:59:59', 'ACTIVE');

INSERT INTO cart (cart_id, customer_id) VALUES
('GH01', 'KH02'),
('GH02', 'KH03'),
('GH03', 'KH04'),
('GH04', 'KH05'),
('GH05', 'KH06');

INSERT INTO cart_item
    (cart_item_id, cart_id, food_id, quantity, note)
VALUES
('CTGH01', 'GH01', 'MA07', 1, 'Không hành'),
('CTGH02', 'GH01', 'MA09', 1, 'Ít đường'),
('CTGH03', 'GH02', 'MA04', 2, NULL),
('CTGH04', 'GH02', 'MA13', 2, NULL),
('CTGH05', 'GH03', 'MA20', 1, 'Cay vừa'),
('CTGH06', 'GH03', 'MA17', 1, NULL),
('CTGH07', 'GH04', 'MA22', 2, 'Đơn chay'),
('CTGH08', 'GH04', 'MA12', 1, NULL),
('CTGH09', 'GH05', 'MA05', 1, NULL),
('CTGH10', 'GH05', 'MA11', 1, NULL);

INSERT INTO cart_item_option (cart_item_id, option_id) VALUES
('CTGH02', 'TC06'),
('CTGH02', 'TC07'),
('CTGH03', 'TC04'),
('CTGH05', 'TC12'),
('CTGH08', 'TC10'),
('CTGH10', 'TC09');

INSERT INTO customer_order
    (order_id, customer_id, assigned_employee_id, promotion_id, ordered_at,
     fulfillment_type, receiver_name, receiver_phone, delivery_address,
     delivery_fee, status)
VALUES
('DH01', 'KH01', 'NV03', 'KM01', '2026-09-01 10:00:00', 'DELIVERY', 'Trần Thị Khách', '0900000002', '12 Nguyễn Trãi, Q.1, TP.HCM', 15000, 'COMPLETED'),
('DH02', 'KH02', 'NV03', NULL,   '2026-09-02 11:30:00', 'PICKUP',   'Nguyễn Văn An',  '0911111111', NULL,                              0, 'COMPLETED'),
('DH03', 'KH03', 'NV04', 'KM02', '2026-09-03 12:15:00', 'DELIVERY', 'Trần Thị Bình',  '0911111112', '40 Nguyễn Huệ, Q.1, TP.HCM',    15000, 'DELIVERING'),
('DH04', 'KH04', 'NV04', NULL,   '2026-09-04 18:45:00', 'DELIVERY', 'Lê Văn Cường',   '0911111113', '12 Võ Văn Tần, Q.3, TP.HCM',    20000, 'PREPARING'),
('DH05', 'KH05', 'NV03', 'KM03', '2026-09-05 19:00:00', 'PICKUP',   'Phạm Thị Dung',  '0911111114', NULL,                              0, 'PENDING_CONFIRMATION'),
('DH06', 'KH06', 'NV04', NULL,   '2026-09-06 09:20:00', 'DELIVERY', 'Hoàng Văn Em',   '0911111115', '56 Xô Viết Nghệ Tĩnh, Bình Thạnh, TP.HCM', 15000, 'COMPLETED'),
('DH07', 'KH07', 'NV03', NULL,   '2026-09-07 20:10:00', 'DELIVERY', 'Đỗ Thị Phương',  '0911111116', '18 Phan Đăng Lưu, Phú Nhuận, TP.HCM', 20000, 'COMPLETED'),
('DH08', 'KH08', 'NV04', NULL,   '2026-09-07 21:00:00', 'PICKUP',   'Vũ Văn Giang',   '0911111117', NULL,                              0, 'CANCELLED'),
('DH09', 'KH09', 'NV03', 'KM06', '2026-09-08 12:40:00', 'DELIVERY', 'Ngô Thị Hoa',    '0911111118', '3 Trường Sơn, Tân Bình, TP.HCM', 15000, 'COMPLETED'),
('DH10', 'KH10', 'NV04', NULL,   '2026-09-08 17:05:00', 'DELIVERY', 'Bùi Văn Inh',    '0911111119', '21 Nguyễn Oanh, Gò Vấp, TP.HCM', 15000, 'DELIVERING'),
('DH11', 'KH11', 'NV03', NULL,   '2026-09-09 08:30:00', 'PICKUP',   'Đặng Thị Kim',   '0911111120', NULL,                              0, 'COMPLETED'),
('DH12', 'KH12', 'NV04', 'KM01', '2026-09-09 13:25:00', 'DELIVERY', 'Trịnh Văn Long', '0911111121', '8 Kha Vạn Cân, Thủ Đức, TP.HCM', 20000, 'COMPLETED'),
('DH13', 'KH13', 'NV03', NULL,   '2026-09-10 19:50:00', 'DELIVERY', 'Lý Thị Mai',     '0911111122', '67 Võ Văn Ngân, Thủ Đức, TP.HCM', 15000, 'PREPARING'),
('DH14', 'KH14', 'NV04', 'KM06', '2026-09-11 11:10:00', 'PICKUP',   'Phan Văn Nam',   '0911111123', NULL,                              0, 'PENDING_CONFIRMATION'),
('DH15', 'KH15', 'NV03', NULL,   '2026-09-11 20:35:00', 'DELIVERY', 'Tô Thị Oanh',    '0911111124', '29 Nguyễn Duy Trinh, Q.2, TP.HCM', 15000, 'COMPLETED');

INSERT INTO order_detail
    (order_detail_id, order_id, food_id, food_name_snapshot, quantity, unit_price)
VALUES
('CTDH01', 'DH01', 'MA01', 'Cơm gà xối mỡ',       2,  53000),
('CTDH02', 'DH01', 'MA02', 'Trà sữa trân châu',   1,  40000),
('CTDH03', 'DH02', 'MA07', 'Cơm tấm sườn bì chả', 1,  42000),
('CTDH04', 'DH02', 'MA09', 'Cà phê sữa đá',       1,  25000),
('CTDH05', 'DH03', 'MA04', 'Phở bò tái',           2,  60000),
('CTDH06', 'DH03', 'MA13', 'Nước chanh muối',      2,  20000),
('CTDH07', 'DH04', 'MA20', 'Lẩu thái hải sản',     1, 230000),
('CTDH08', 'DH04', 'MA17', 'Khoai tây chiên',      1,  28000),
('CTDH09', 'DH05', 'MA22', 'Đậu hũ sốt cà',        1,  30000),
('CTDH10', 'DH05', 'MA23', 'Rau củ xào chay',      1,  28000),
('CTDH11', 'DH06', 'MA05', 'Bún chả Hà Nội',       2,  48000),
('CTDH12', 'DH06', 'MA12', 'Trà đào cam sả',       2,  43000),
('CTDH13', 'DH07', 'MA21', 'Nướng BBQ thập cẩm',   1, 220000),
('CTDH14', 'DH07', 'MA10', 'Nước ép cam',          2,  30000),
('CTDH15', 'DH08', 'MA18', 'Gà rán giòn',          1,  50000),
('CTDH16', 'DH09', 'MA06', 'Mì xào hải sản',       1,  55000),
('CTDH17', 'DH09', 'MA14', 'Chè khúc bạch',        2,  25000),
('CTDH18', 'DH10', 'MA08', 'Bún bò Huế',           2,  47000),
('CTDH19', 'DH10', 'MA11', 'Sinh tố bơ',           1,  32000),
('CTDH20', 'DH11', 'MA03', 'Bánh flan',            3,  15000),
('CTDH21', 'DH11', 'MA16', 'Bánh su kem',          2,  12000),
('CTDH22', 'DH12', 'MA01', 'Cơm gà xối mỡ',        1,  45000),
('CTDH23', 'DH12', 'MA02', 'Trà sữa trân châu',    1,  35000),
('CTDH24', 'DH13', 'MA19', 'Xúc xích nướng',       3,  20000),
('CTDH25', 'DH13', 'MA15', 'Kem dừa',              1,  20000),
('CTDH26', 'DH14', 'MA04', 'Phở bò tái',           1,  50000),
('CTDH27', 'DH15', 'MA20', 'Lẩu thái hải sản',     1, 180000),
('CTDH28', 'DH15', 'MA13', 'Nước chanh muối',      1,  20000);

INSERT INTO order_detail_option
    (order_detail_id, option_id, option_name_snapshot, extra_price_snapshot)
VALUES
('CTDH01', 'TC03', 'Thêm trứng ốp la',  8000),
('CTDH02', 'TC01', 'Size L',             5000),
('CTDH02', 'TC02', '50% đường',             0),
('CTDH05', 'TC04', 'Tô lớn',            10000),
('CTDH07', 'TC12', 'Thêm hải sản',      50000),
('CTDH12', 'TC10', 'Size L',             5000),
('CTDH15', 'TC11', 'Sốt phô mai',        5000);

INSERT INTO order_status_history
    (history_id, order_id, status, changed_at, changed_by_employee_id)
VALUES
('LS01', 'DH01', 'PENDING_CONFIRMATION', '2026-09-01 10:00:00', 'NV03'),
('LS02', 'DH01', 'PREPARING',            '2026-09-01 10:15:00', 'NV02'),
('LS03', 'DH01', 'DELIVERING',           '2026-09-01 10:40:00', 'NV07'),
('LS04', 'DH01', 'COMPLETED',            '2026-09-01 11:10:00', 'NV07'),
('LS05', 'DH02', 'PENDING_CONFIRMATION', '2026-09-02 11:30:00', 'NV03'),
('LS06', 'DH02', 'PREPARING',            '2026-09-02 11:40:00', 'NV02'),
('LS07', 'DH02', 'COMPLETED',            '2026-09-02 12:05:00', 'NV03'),
('LS08', 'DH03', 'PENDING_CONFIRMATION', '2026-09-03 12:15:00', 'NV04'),
('LS09', 'DH03', 'PREPARING',            '2026-09-03 12:25:00', 'NV08'),
('LS10', 'DH03', 'DELIVERING',           '2026-09-03 12:50:00', 'NV07'),
('LS11', 'DH04', 'PENDING_CONFIRMATION', '2026-09-04 18:45:00', 'NV04'),
('LS12', 'DH04', 'PREPARING',            '2026-09-04 18:55:00', 'NV08'),
('LS13', 'DH05', 'PENDING_CONFIRMATION', '2026-09-05 19:00:00', 'NV03'),
('LS14', 'DH06', 'PENDING_CONFIRMATION', '2026-09-06 09:20:00', 'NV04'),
('LS15', 'DH06', 'PREPARING',            '2026-09-06 09:30:00', 'NV02'),
('LS16', 'DH06', 'DELIVERING',           '2026-09-06 09:55:00', 'NV07'),
('LS17', 'DH06', 'COMPLETED',            '2026-09-06 10:30:00', 'NV07'),
('LS18', 'DH07', 'PENDING_CONFIRMATION', '2026-09-07 20:10:00', 'NV03'),
('LS19', 'DH07', 'PREPARING',            '2026-09-07 20:25:00', 'NV08'),
('LS20', 'DH07', 'DELIVERING',           '2026-09-07 20:50:00', 'NV07'),
('LS21', 'DH07', 'COMPLETED',            '2026-09-07 21:30:00', 'NV07'),
('LS22', 'DH08', 'PENDING_CONFIRMATION', '2026-09-07 21:00:00', 'NV04'),
('LS23', 'DH08', 'CANCELLED',            '2026-09-07 21:10:00', 'NV04'),
('LS24', 'DH09', 'PENDING_CONFIRMATION', '2026-09-08 12:40:00', 'NV03'),
('LS25', 'DH09', 'PREPARING',            '2026-09-08 12:50:00', 'NV02'),
('LS26', 'DH09', 'DELIVERING',           '2026-09-08 13:15:00', 'NV07'),
('LS27', 'DH09', 'COMPLETED',            '2026-09-08 13:45:00', 'NV07'),
('LS28', 'DH10', 'PENDING_CONFIRMATION', '2026-09-08 17:05:00', 'NV04'),
('LS29', 'DH10', 'PREPARING',            '2026-09-08 17:15:00', 'NV08'),
('LS30', 'DH10', 'DELIVERING',           '2026-09-08 17:40:00', 'NV07'),
('LS31', 'DH11', 'PENDING_CONFIRMATION', '2026-09-09 08:30:00', 'NV03'),
('LS32', 'DH11', 'PREPARING',            '2026-09-09 08:40:00', 'NV02'),
('LS33', 'DH11', 'COMPLETED',            '2026-09-09 09:00:00', 'NV03'),
('LS34', 'DH12', 'PENDING_CONFIRMATION', '2026-09-09 13:25:00', 'NV04'),
('LS35', 'DH12', 'PREPARING',            '2026-09-09 13:35:00', 'NV08'),
('LS36', 'DH12', 'DELIVERING',           '2026-09-09 14:00:00', 'NV07'),
('LS37', 'DH12', 'COMPLETED',            '2026-09-09 14:35:00', 'NV07'),
('LS38', 'DH13', 'PENDING_CONFIRMATION', '2026-09-10 19:50:00', 'NV03'),
('LS39', 'DH13', 'PREPARING',            '2026-09-10 20:00:00', 'NV02'),
('LS40', 'DH14', 'PENDING_CONFIRMATION', '2026-09-11 11:10:00', 'NV04'),
('LS41', 'DH15', 'PENDING_CONFIRMATION', '2026-09-11 20:35:00', 'NV03'),
('LS42', 'DH15', 'PREPARING',            '2026-09-11 20:45:00', 'NV08'),
('LS43', 'DH15', 'DELIVERING',           '2026-09-11 21:10:00', 'NV07'),
('LS44', 'DH15', 'COMPLETED',            '2026-09-11 21:50:00', 'NV07');

-- amount matches customer_order.total_amount after order_detail triggers run.
INSERT INTO payment
    (payment_id, order_id, payment_method, status,
     amount, paid_at, transaction_ref)
VALUES
('TT01', 'DH01', 'E_WALLET',     'SUCCESS',  146400, '2026-09-01 10:01:00', 'VDT-DH01'),
('TT02', 'DH02', 'CASH',         'SUCCESS',   67000, '2026-09-02 12:05:00', NULL),
('TT03', 'DH03', 'BANK_TRANSFER','SUCCESS',  155000, '2026-09-03 12:16:00', 'CK-DH03'),
('TT04', 'DH04', 'CASH',         'PENDING',  278000, NULL,                  NULL),
('TT05', 'DH05', 'E_WALLET',     'PENDING',   49300, NULL,                  'VDT-DH05'),
('TT06', 'DH06', 'BANK_TRANSFER','SUCCESS',  197000, '2026-09-06 09:21:00', 'CK-DH06'),
('TT07', 'DH07', 'CASH',         'SUCCESS',  300000, '2026-09-07 21:30:00', NULL),
('TT08', 'DH08', 'E_WALLET',     'REFUNDED',  50000, '2026-09-07 21:01:00', 'VDT-DH08'),
('TT09', 'DH09', 'BANK_TRANSFER','SUCCESS',  114750, '2026-09-08 12:41:00', 'CK-DH09'),
('TT10', 'DH10', 'CASH',         'PENDING',  141000, NULL,                  NULL),
('TT11', 'DH11', 'E_WALLET',     'SUCCESS',   69000, '2026-09-09 08:31:00', 'VDT-DH11'),
('TT12', 'DH12', 'BANK_TRANSFER','SUCCESS',   92000, '2026-09-09 13:26:00', 'CK-DH12'),
('TT13', 'DH13', 'CASH',         'PENDING',   95000, NULL,                  NULL),
('TT14', 'DH14', 'E_WALLET',     'PENDING',   47500, NULL,                  'VDT-DH14'),
('TT15', 'DH15', 'BANK_TRANSFER','SUCCESS',  215000, '2026-09-11 20:36:00', 'CK-DH15');

COMMIT;
