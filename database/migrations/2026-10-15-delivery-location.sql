-- Additive and repeatable. Historical orders keep NULL location snapshots.
SET @delivery_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'customer_order' AND column_name = 'delivery_latitude') = 0,
    'ALTER TABLE customer_order ADD COLUMN delivery_latitude DOUBLE NULL', 'SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl;
EXECUTE delivery_stmt;
DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'customer_order' AND column_name = 'delivery_longitude') = 0,
    'ALTER TABLE customer_order ADD COLUMN delivery_longitude DOUBLE NULL', 'SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl;
EXECUTE delivery_stmt;
DEALLOCATE PREPARE delivery_stmt;
SET @delivery_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'customer_order' AND column_name = 'delivery_distance_km') = 0,
    'ALTER TABLE customer_order ADD COLUMN delivery_distance_km DECIMAL(8,3) NULL', 'SELECT 1');
PREPARE delivery_stmt FROM @delivery_ddl;
EXECUTE delivery_stmt;
DEALLOCATE PREPARE delivery_stmt;
