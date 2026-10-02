-- Development bootstrap for MySQL 8.0.16+.
-- This script recreates the database and therefore deletes existing data.
DROP DATABASE IF EXISTS crave;
CREATE DATABASE crave
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE crave;
SET NAMES utf8mb4;

-- Base table for the abstract User class. Customer and employee share one
-- login namespace, so email and phone are unique across both account types.
CREATE TABLE user_account (
    user_id         VARCHAR(10)     NOT NULL,
    account_type    ENUM('CUSTOMER','EMPLOYEE') NOT NULL,
    full_name       VARCHAR(100)    NOT NULL,
    email           VARCHAR(100)    NOT NULL,
    phone           VARCHAR(15)     NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_account PRIMARY KEY (user_id),
    CONSTRAINT uq_user_account_email UNIQUE (email),
    CONSTRAINT uq_user_account_phone UNIQUE (phone)
) ENGINE=InnoDB;

CREATE TABLE customer (
    customer_id     VARCHAR(10)     NOT NULL,
    status          ENUM('ACTIVE','LOCKED') NOT NULL DEFAULT 'ACTIVE',
    registered_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer PRIMARY KEY (customer_id),
    CONSTRAINT fk_customer_account FOREIGN KEY (customer_id)
        REFERENCES user_account(user_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE employee (
    employee_id     VARCHAR(10)     NOT NULL,
    role            ENUM(
                        'ADMIN',
                        'MENU_MANAGER',
                        'ORDER_STAFF',
                        'PROMOTION_MANAGER',
                        'HR_MANAGER'
                    ) NOT NULL,
    address         VARCHAR(255)    NULL,
    hire_date       DATE            NOT NULL,
    status          ENUM('WORKING','ON_LEAVE') NOT NULL DEFAULT 'WORKING',
    CONSTRAINT pk_employee PRIMARY KEY (employee_id),
    CONSTRAINT fk_employee_account FOREIGN KEY (employee_id)
        REFERENCES user_account(user_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE category (
    category_id     VARCHAR(10)     NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    description     VARCHAR(255)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_category PRIMARY KEY (category_id),
    CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE food (
    food_id         VARCHAR(10)     NOT NULL,
    category_id     VARCHAR(10)     NOT NULL,
    name            VARCHAR(150)    NOT NULL,
    price           DECIMAL(12,0)   NOT NULL,
    image_url       VARCHAR(512)    NULL,
    description     VARCHAR(500)    NULL,
    status          ENUM('AVAILABLE','UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_food PRIMARY KEY (food_id),
    CONSTRAINT fk_food_category FOREIGN KEY (category_id)
        REFERENCES category(category_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_food_price CHECK (price > 0)
) ENGINE=InnoDB;

CREATE TABLE food_option (
    option_id       VARCHAR(10)     NOT NULL,
    food_id         VARCHAR(10)     NOT NULL,
    option_type     ENUM('SIZE','TOPPING','SUGAR_LEVEL','ICE_LEVEL','OTHER')
                    NOT NULL,
    name            VARCHAR(100)    NOT NULL,
    extra_price     DECIMAL(12,0)   NOT NULL DEFAULT 0,
    status          ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                    ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_food_option PRIMARY KEY (option_id),
    CONSTRAINT uq_food_option_food_name UNIQUE (food_id, option_type, name),
    CONSTRAINT fk_food_option_food FOREIGN KEY (food_id)
        REFERENCES food(food_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_food_option_extra_price CHECK (extra_price >= 0)
) ENGINE=InnoDB;

CREATE TABLE delivery_address (
    address_id          VARCHAR(10)     NOT NULL,
    customer_id         VARCHAR(10)     NOT NULL,
    address_line        VARCHAR(255)    NOT NULL,
    note                VARCHAR(255)    NULL,
    is_default          TINYINT(1)      NOT NULL DEFAULT 0,
    -- MySQL allows multiple NULL values in a UNIQUE index. This generated
    -- value therefore enforces at most one default address per customer.
    default_customer_id VARCHAR(10) GENERATED ALWAYS AS (
                            CASE WHEN is_default = 1 THEN customer_id ELSE NULL END
                        ) STORED,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_delivery_address PRIMARY KEY (address_id),
    CONSTRAINT uq_delivery_address_default UNIQUE (default_customer_id),
    CONSTRAINT fk_delivery_address_customer FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_delivery_address_is_default CHECK (is_default IN (0, 1)),
    CONSTRAINT ck_delivery_address_line CHECK (TRIM(address_line) <> '')
) ENGINE=InnoDB;

CREATE TABLE promotion (
    promotion_id        VARCHAR(10)     NOT NULL,
    code                VARCHAR(30)     NOT NULL,
    name                VARCHAR(150)    NOT NULL,
    discount_type       ENUM('PERCENT','FIXED_AMOUNT') NOT NULL,
    discount_value      DECIMAL(12,0)   NOT NULL,
    minimum_order_value DECIMAL(12,0)   NOT NULL DEFAULT 0,
    maximum_discount    DECIMAL(12,0)   NULL,
    start_at            DATETIME        NOT NULL,
    end_at              DATETIME        NOT NULL,
    status              ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_promotion PRIMARY KEY (promotion_id),
    CONSTRAINT uq_promotion_code UNIQUE (code),
    CONSTRAINT ck_promotion_discount CHECK (
        (discount_type = 'PERCENT' AND discount_value > 0 AND discount_value <= 100)
        OR (discount_type = 'FIXED_AMOUNT' AND discount_value > 0)
    ),
    CONSTRAINT ck_promotion_minimum CHECK (minimum_order_value >= 0),
    CONSTRAINT ck_promotion_maximum CHECK (
        maximum_discount IS NULL OR maximum_discount > 0
    ),
    CONSTRAINT ck_promotion_period CHECK (end_at > start_at)
) ENGINE=InnoDB;

CREATE TABLE cart (
    cart_id          VARCHAR(10)     NOT NULL,
    customer_id      VARCHAR(10)     NOT NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_cart PRIMARY KEY (cart_id),
    CONSTRAINT uq_cart_customer UNIQUE (customer_id),
    CONSTRAINT fk_cart_customer FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id) ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE cart_item (
    cart_item_id     VARCHAR(10)     NOT NULL,
    cart_id          VARCHAR(10)     NOT NULL,
    food_id          VARCHAR(10)     NOT NULL,
    quantity         INT             NOT NULL DEFAULT 1,
    note             VARCHAR(255)    NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_cart_item PRIMARY KEY (cart_item_id),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id)
        REFERENCES cart(cart_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_food FOREIGN KEY (food_id)
        REFERENCES food(food_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_cart_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

-- The service must verify that the option belongs to cart_item.food_id.
-- Keeping only the two real foreign keys makes JDBC/JPA mapping straightforward.
CREATE TABLE cart_item_option (
    cart_item_option_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    cart_item_id        VARCHAR(10)     NOT NULL,
    option_id           VARCHAR(10)     NOT NULL,
    CONSTRAINT pk_cart_item_option PRIMARY KEY (cart_item_option_id),
    CONSTRAINT uq_cart_item_option UNIQUE (cart_item_id, option_id),
    CONSTRAINT fk_cart_item_option_item FOREIGN KEY (cart_item_id)
        REFERENCES cart_item(cart_item_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_option_option FOREIGN KEY (option_id)
        REFERENCES food_option(option_id) ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE customer_order (
    order_id             VARCHAR(10)     NOT NULL,
    customer_id          VARCHAR(10)     NOT NULL,
    assigned_employee_id VARCHAR(10)     NULL,
    promotion_id         VARCHAR(10)     NULL,
    ordered_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fulfillment_type     ENUM('DELIVERY','PICKUP') NOT NULL,
    receiver_name        VARCHAR(100)    NOT NULL,
    receiver_phone       VARCHAR(15)     NOT NULL,
    delivery_address     VARCHAR(255)    NULL,
    customer_note        VARCHAR(255)    NULL,
    subtotal             DECIMAL(12,0)   NOT NULL DEFAULT 0,
    discount_amount      DECIMAL(12,0)   NOT NULL DEFAULT 0,
    delivery_fee         DECIMAL(12,0)   NOT NULL DEFAULT 0,
    total_amount         DECIMAL(12,0) GENERATED ALWAYS AS (
                             subtotal - discount_amount + delivery_fee
                         ) STORED,
    status               ENUM(
                             'PENDING_CONFIRMATION','PREPARING','DELIVERING',
                             'COMPLETED','CANCELLED'
                         ) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                         ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_order PRIMARY KEY (order_id),
    CONSTRAINT fk_customer_order_customer FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_customer_order_employee FOREIGN KEY (assigned_employee_id)
        REFERENCES employee(employee_id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT fk_customer_order_promotion FOREIGN KEY (promotion_id)
        REFERENCES promotion(promotion_id) ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_customer_order_subtotal CHECK (subtotal >= 0),
    CONSTRAINT ck_customer_order_discount CHECK (
        discount_amount >= 0 AND discount_amount <= subtotal
    ),
    CONSTRAINT ck_customer_order_delivery_fee CHECK (delivery_fee >= 0),
    CONSTRAINT ck_customer_order_receiver_name CHECK (TRIM(receiver_name) <> ''),
    CONSTRAINT ck_customer_order_receiver_phone CHECK (TRIM(receiver_phone) <> ''),
    CONSTRAINT ck_customer_order_delivery_address CHECK (
        (fulfillment_type = 'DELIVERY'
            AND delivery_address IS NOT NULL
            AND TRIM(delivery_address) <> '')
        OR (fulfillment_type = 'PICKUP' AND delivery_address IS NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE order_detail (
    order_detail_id    VARCHAR(10)     NOT NULL,
    order_id           VARCHAR(10)     NOT NULL,
    food_id            VARCHAR(10)     NOT NULL,
    food_name_snapshot VARCHAR(150)    NOT NULL,
    quantity           INT             NOT NULL,
    -- Unit price already includes every selected option for one serving.
    unit_price         DECIMAL(12,0)   NOT NULL,
    line_total         DECIMAL(12,0) GENERATED ALWAYS AS (
                           quantity * unit_price
                       ) STORED,
    note               VARCHAR(255)    NULL,
    CONSTRAINT pk_order_detail PRIMARY KEY (order_detail_id),
    CONSTRAINT fk_order_detail_order FOREIGN KEY (order_id)
        REFERENCES customer_order(order_id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_order_detail_food FOREIGN KEY (food_id)
        REFERENCES food(food_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT ck_order_detail_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_detail_unit_price CHECK (unit_price > 0)
) ENGINE=InnoDB;

CREATE TABLE order_detail_option (
    order_detail_option_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_detail_id        VARCHAR(10)     NOT NULL,
    option_id              VARCHAR(10)     NULL,
    option_name_snapshot   VARCHAR(100)    NOT NULL,
    extra_price_snapshot   DECIMAL(12,0)   NOT NULL DEFAULT 0,
    CONSTRAINT pk_order_detail_option PRIMARY KEY (order_detail_option_id),
    CONSTRAINT uq_order_detail_option UNIQUE (order_detail_id, option_id),
    CONSTRAINT fk_order_detail_option_detail FOREIGN KEY (order_detail_id)
        REFERENCES order_detail(order_detail_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_order_detail_option_option FOREIGN KEY (option_id)
        REFERENCES food_option(option_id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT ck_order_detail_option_price CHECK (extra_price_snapshot >= 0)
) ENGINE=InnoDB;

CREATE TABLE order_status_history (
    history_id             VARCHAR(10)     NOT NULL,
    order_id               VARCHAR(10)     NOT NULL,
    status                 ENUM(
                               'PENDING_CONFIRMATION','PREPARING','DELIVERING',
                               'COMPLETED','CANCELLED'
                           ) NOT NULL,
    changed_at             DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_employee_id VARCHAR(10)     NULL,
    note                   VARCHAR(255)    NULL,
    CONSTRAINT pk_order_status_history PRIMARY KEY (history_id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id)
        REFERENCES customer_order(order_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_order_status_history_employee FOREIGN KEY (changed_by_employee_id)
        REFERENCES employee(employee_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE payment (
    payment_id       VARCHAR(12)     NOT NULL,
    order_id         VARCHAR(10)     NOT NULL,
    payment_method   ENUM('CASH','BANK_TRANSFER','E_WALLET') NOT NULL,
    status           ENUM('PENDING','SUCCESS','FAILED','REFUNDED')
                     NOT NULL DEFAULT 'PENDING',
    amount           DECIMAL(12,0)   NOT NULL,
    paid_at          DATETIME        NULL,
    transaction_ref  VARCHAR(100)    NULL,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment PRIMARY KEY (payment_id),
    CONSTRAINT uq_payment_order UNIQUE (order_id),
    CONSTRAINT uq_payment_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id)
        REFERENCES customer_order(order_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT ck_payment_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_paid_at CHECK (
        (status IN ('SUCCESS','REFUNDED') AND paid_at IS NOT NULL)
        OR status IN ('PENDING','FAILED')
    )
) ENGINE=InnoDB;

CREATE INDEX idx_user_account_full_name ON user_account(full_name);
CREATE INDEX idx_food_category_status ON food(category_id, status);
CREATE INDEX idx_food_name ON food(name);
CREATE INDEX idx_promotion_lookup ON promotion(code, status, start_at, end_at);
CREATE INDEX idx_order_customer_date ON customer_order(customer_id, ordered_at);
CREATE INDEX idx_order_status_date ON customer_order(status, ordered_at);
CREATE INDEX idx_order_employee_status
    ON customer_order(assigned_employee_id, status);
CREATE INDEX idx_order_detail_food ON order_detail(food_id);
CREATE INDEX idx_order_history_date ON order_status_history(order_id, changed_at);

-- Server-side cart totals. DAO code can query this view instead of accepting
-- any price or total sent by the browser.
CREATE VIEW v_cart_item_total AS
SELECT
    ci.cart_item_id,
    ci.cart_id,
    ci.food_id,
    f.name AS food_name,
    ci.quantity,
    ci.note,
    f.price AS base_price,
    COALESCE(SUM(fo.extra_price), 0) AS option_total,
    (f.price + COALESCE(SUM(fo.extra_price), 0)) * ci.quantity AS line_total
FROM cart_item ci
JOIN food f ON f.food_id = ci.food_id
LEFT JOIN cart_item_option cio ON cio.cart_item_id = ci.cart_item_id
LEFT JOIN food_option fo ON fo.option_id = cio.option_id
GROUP BY
    ci.cart_item_id, ci.cart_id, ci.food_id, f.name,
    ci.quantity, ci.note, f.price;

CREATE VIEW v_cart_summary AS
SELECT
    c.cart_id,
    c.customer_id,
    COALESCE(SUM(v.line_total), 0) AS subtotal
FROM cart c
LEFT JOIN v_cart_item_total v ON v.cart_id = c.cart_id
GROUP BY c.cart_id, c.customer_id;

DELIMITER $$

CREATE PROCEDURE sp_calculate_discount(
    IN p_promotion_id VARCHAR(10),
    IN p_ordered_at DATETIME,
    IN p_subtotal DECIMAL(12,0),
    OUT p_discount_amount DECIMAL(12,0)
)
BEGIN
    DECLARE v_discount_type VARCHAR(20) DEFAULT NULL;
    DECLARE v_discount_value DECIMAL(12,0) DEFAULT NULL;
    DECLARE v_minimum_order_value DECIMAL(12,0) DEFAULT 0;
    DECLARE v_maximum_discount DECIMAL(12,0) DEFAULT NULL;
    DECLARE v_start_at DATETIME DEFAULT NULL;
    DECLARE v_end_at DATETIME DEFAULT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_discount_value = NULL;

    SET p_discount_amount = 0;

    IF p_promotion_id IS NOT NULL AND p_subtotal > 0 THEN
        SELECT discount_type, discount_value, minimum_order_value,
               maximum_discount, start_at, end_at
          INTO v_discount_type, v_discount_value, v_minimum_order_value,
               v_maximum_discount, v_start_at, v_end_at
          FROM promotion
         WHERE promotion_id = p_promotion_id;
    END IF;

    IF v_discount_value IS NOT NULL
       AND p_subtotal >= COALESCE(v_minimum_order_value, 0)
       AND p_ordered_at BETWEEN v_start_at AND v_end_at THEN
        IF v_discount_type = 'PERCENT' THEN
            SET p_discount_amount = ROUND(p_subtotal * v_discount_value / 100, 0);
        ELSE
            SET p_discount_amount = v_discount_value;
        END IF;

        IF v_maximum_discount IS NOT NULL THEN
            SET p_discount_amount = LEAST(p_discount_amount, v_maximum_discount);
        END IF;

        SET p_discount_amount = LEAST(p_discount_amount, p_subtotal);
    END IF;
END$$

CREATE TRIGGER trg_customer_order_bi_discount
BEFORE INSERT ON customer_order
FOR EACH ROW
BEGIN
    DECLARE v_discount_amount DECIMAL(12,0) DEFAULT 0;
    CALL sp_calculate_discount(
        NEW.promotion_id, NEW.ordered_at, NEW.subtotal, v_discount_amount
    );
    SET NEW.discount_amount = v_discount_amount;
END$$

CREATE TRIGGER trg_customer_order_bu_discount
BEFORE UPDATE ON customer_order
FOR EACH ROW
BEGIN
    DECLARE v_discount_amount DECIMAL(12,0) DEFAULT 0;
    IF NOT (OLD.promotion_id <=> NEW.promotion_id)
       OR OLD.ordered_at <> NEW.ordered_at
       OR OLD.subtotal <> NEW.subtotal THEN
        CALL sp_calculate_discount(
            NEW.promotion_id, NEW.ordered_at, NEW.subtotal, v_discount_amount
        );
        SET NEW.discount_amount = v_discount_amount;
    ELSE
        -- Status/profile updates must not silently rewrite an order snapshot.
        SET NEW.discount_amount = OLD.discount_amount;
    END IF;
END$$

CREATE PROCEDURE sp_refresh_order_totals(IN p_order_id VARCHAR(10))
BEGIN
    DECLARE v_subtotal DECIMAL(12,0) DEFAULT 0;

    SELECT COALESCE(SUM(line_total), 0)
      INTO v_subtotal
      FROM order_detail
     WHERE order_id = p_order_id;

    UPDATE customer_order
       SET subtotal = v_subtotal
     WHERE order_id = p_order_id;
END$$

CREATE TRIGGER trg_order_detail_ai_total
AFTER INSERT ON order_detail
FOR EACH ROW
BEGIN
    CALL sp_refresh_order_totals(NEW.order_id);
END$$

CREATE TRIGGER trg_order_detail_au_total
AFTER UPDATE ON order_detail
FOR EACH ROW
BEGIN
    CALL sp_refresh_order_totals(NEW.order_id);
    IF OLD.order_id <> NEW.order_id THEN
        CALL sp_refresh_order_totals(OLD.order_id);
    END IF;
END$$

CREATE TRIGGER trg_order_detail_ad_total
AFTER DELETE ON order_detail
FOR EACH ROW
BEGIN
    CALL sp_refresh_order_totals(OLD.order_id);
END$$

DELIMITER ;
