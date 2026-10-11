-- Issue #23: inactive promotions must not produce a discount in database triggers.
-- Apply to an existing database using the account that owns the routine.
-- Reapply any routine-specific EXECUTE grants after replacing the procedure.
USE crave;

DROP PROCEDURE IF EXISTS sp_calculate_discount;

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
        WHERE promotion_id = p_promotion_id
          AND status = 'ACTIVE';
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

DELIMITER ;
