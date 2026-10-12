USE crave;
INSERT INTO user_account (user_id, account_type, full_name, email, phone, password_hash)
VALUES ('MKH01', 'CUSTOMER', 'Migration fixture', 'migration@example.test', '0987654321', 'test-only');
INSERT INTO customer (customer_id) VALUES ('MKH01');
INSERT INTO promotion (promotion_id, code, name, discount_type, discount_value, start_at, end_at, status)
VALUES
('MKM01', 'MIGRATION_INACTIVE', 'Inactive migration fixture', 'FIXED_AMOUNT', 20000,
 '2026-01-01', '2026-12-31', 'INACTIVE'),
('MKM02', 'MIGRATION_ACTIVE', 'Active migration fixture', 'FIXED_AMOUNT', 20000,
 '2026-01-01', '2026-12-31', 'ACTIVE');
INSERT INTO customer_order
    (order_id, customer_id, promotion_id, ordered_at, fulfillment_type, receiver_name, receiver_phone, subtotal)
VALUES
('MDH01', 'MKH01', 'MKM01', '2026-10-01 10:00:00', 'PICKUP', 'Migration fixture', '0987654321', 50000),
('MDH02', 'MKH01', 'MKM02', '2026-10-01 10:00:00', 'PICKUP', 'Migration fixture', '0987654321', 50000);
