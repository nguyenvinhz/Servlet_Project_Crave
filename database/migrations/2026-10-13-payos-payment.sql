-- Add durable payOS payment-link reservations without changing existing orders/payments.
-- Safe to reapply. Run with the database migration account before starting the new WAR.
USE crave;

SET @payos_table_exists = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'payos_payment_link'
);

CREATE TABLE IF NOT EXISTS payos_payment_link (
    order_code       BIGINT          NOT NULL AUTO_INCREMENT,
    payment_id       VARCHAR(12)     NOT NULL,
    amount           BIGINT          NOT NULL,
    currency         VARCHAR(3)      NOT NULL DEFAULT 'VND',
    description      VARCHAR(25)     NOT NULL,
    transfer_description VARCHAR(255) NULL,
    payment_link_id  VARCHAR(64)     NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'CREATING',
    checkout_url     VARCHAR(1024)   NULL,
    qr_code          TEXT            NULL,
    account_number   VARCHAR(50)     NULL,
    account_name     VARCHAR(150)    NULL,
    bin              VARCHAR(20)     NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_payos_payment_link PRIMARY KEY (order_code),
    CONSTRAINT uq_payos_payment UNIQUE (payment_id),
    CONSTRAINT uq_payos_link_id UNIQUE (payment_link_id),
    CONSTRAINT fk_payos_payment FOREIGN KEY (payment_id)
        REFERENCES payment(payment_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_payos_amount CHECK (amount > 0),
    CONSTRAINT ck_payos_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_payos_status CHECK (status IN ('CREATING','PENDING','PROCESSING','PAID','CANCELLED','EXPIRED'))
) ENGINE=InnoDB AUTO_INCREMENT=1000000000000;

-- A fresh installation receives a random 13-digit sequence origin, avoiding
-- reuse of an older installation's codes under the same payOS merchant account.
-- Existing sequences and reservations remain unchanged on migration replay.
SET @payos_sequence_start = CAST(1000000000000 + FLOOR(RAND() * 7000000000000) AS UNSIGNED);
SET @payos_sequence_sql = IF(@payos_table_exists = 0,
    CONCAT('ALTER TABLE payos_payment_link AUTO_INCREMENT = ', @payos_sequence_start),
    'DO 0');
PREPARE payos_sequence_statement FROM @payos_sequence_sql;
EXECUTE payos_sequence_statement;
DEALLOCATE PREPARE payos_sequence_statement;
