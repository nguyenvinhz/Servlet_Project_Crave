-- Apply once before deploying the PayOS integration; safe to run again on MySQL 8.
USE crave;
SET @statement = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment' AND COLUMN_NAME = 'payos_order_code') = 0,
    'ALTER TABLE payment ADD COLUMN payos_order_code BIGINT NULL, ADD COLUMN payos_payment_link_id VARCHAR(100) NULL, ADD COLUMN payos_checkout_url VARCHAR(512) NULL, ADD UNIQUE INDEX uq_payment_payos_order_code (payos_order_code), ADD UNIQUE INDEX uq_payment_payos_link (payos_payment_link_id)',
    'SELECT 1');
PREPARE payos_migration FROM @statement;
EXECUTE payos_migration;
DEALLOCATE PREPARE payos_migration;
