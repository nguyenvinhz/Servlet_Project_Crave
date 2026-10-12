-- Fixtures belong only to the freshly recreated CI database.
USE crave;
INSERT INTO category (category_id, name) VALUES ('CI_CAT', 'CI smoke category');
INSERT INTO food (food_id, category_id, name, price)
VALUES ('CI_FOOD', 'CI_CAT', 'CI smoke bowl', 40000);
INSERT INTO food_option (option_id, food_id, option_type, name, extra_price)
VALUES ('CI_OPT', 'CI_FOOD', 'TOPPING', 'CI smoke topping', 5000);
INSERT INTO promotion
    (promotion_id, code, name, discount_type, discount_value, minimum_order_value, start_at, end_at)
VALUES ('CI_PROMO', 'CI_SMOKE_20K', 'CI smoke promotion', 'FIXED_AMOUNT', 20000, 100000,
        NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY);
