#!/usr/bin/env bash
# Upgrade a frozen pre-migration schema, preserving existing order snapshots.
set -Eeuo pipefail
[[ ${CI_DISPOSABLE_DATABASE:-} == true ]] || {
  printf 'Set CI_DISPOSABLE_DATABASE=true only for a disposable test server.\n' >&2
  exit 1
}
cd "$(dirname "$0")/../.."
source scripts/ci/mysql-client.sh

assert_sql() {
  local expected=$1 query=$2 actual
  actual=$(mysql_root crave --execute="$query")
  [[ $actual == "$expected" ]] || {
    printf 'Migration assertion failed: expected %s, got %s\n' "$expected" "$actual" >&2
    exit 1
  }
}

mysql_root < database/tests/baseline-before-active-promotion.sql
mysql_root < database/tests/migration-fixtures.sql
assert_sql 20000 "SELECT discount_amount FROM customer_order WHERE order_id='MDH01'"
assert_sql 20000 "SELECT discount_amount FROM customer_order WHERE order_id='MDH02'"
before=$(mysql_dump | sha256sum)

mysql_root < database/migrations/2026-10-11-active-promotion-discount.sql
after=$(mysql_dump | sha256sum)
[[ $before == "$after" ]] || { printf 'Migration changed existing data.\n' >&2; exit 1; }

assert_sql 0 "CALL sp_calculate_discount('MKM01', '2026-10-01 10:00:00', 50000, @discount); SELECT @discount"
assert_sql 20000 "CALL sp_calculate_discount('MKM02', '2026-10-01 10:00:00', 50000, @discount); SELECT @discount"
mysql_root crave <<'SQL'
UPDATE customer_order SET status='PREPARING' WHERE order_id='MDH01';
INSERT INTO customer_order
    (order_id, customer_id, promotion_id, ordered_at, fulfillment_type, receiver_name, receiver_phone, subtotal)
SELECT 'MDH03', customer_id, promotion_id, ordered_at, fulfillment_type, receiver_name, receiver_phone, subtotal
FROM customer_order WHERE order_id='MDH01';
INSERT INTO customer_order
    (order_id, customer_id, promotion_id, ordered_at, fulfillment_type, receiver_name, receiver_phone, subtotal)
SELECT 'MDH04', customer_id, promotion_id, ordered_at, fulfillment_type, receiver_name, receiver_phone, subtotal
FROM customer_order WHERE order_id='MDH02';
SQL
assert_sql 20000 "SELECT discount_amount FROM customer_order WHERE order_id='MDH01'"
assert_sql 0 "SELECT discount_amount FROM customer_order WHERE order_id='MDH03'"
assert_sql 20000 "SELECT discount_amount FROM customer_order WHERE order_id='MDH04'"

# Database-wide EXECUTE covers a routine recreated by DROP/CREATE.
mysql_root < scripts/ci/application-user.sql
printf 'Migration passed: existing rows preserved, old snapshots unchanged, new discounts corrected.\n'
