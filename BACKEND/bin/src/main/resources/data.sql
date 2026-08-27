-- Seed initial Fixed Deposit Products
INSERT INTO fd_products (product_name, min_amount, max_amount, min_term_days, max_term_days, base_interest_rate, is_active)
VALUES ('Short Term Saver', 1000.00, 500000.00, 7, 180, 4.50, true)
ON DUPLICATE KEY UPDATE base_interest_rate = 4.50;

INSERT INTO fd_products (product_name, min_amount, max_amount, min_term_days, max_term_days, base_interest_rate, is_active)
VALUES ('Standard Growth FD', 5000.00, 1000000.00, 181, 365, 6.25, true)
ON DUPLICATE KEY UPDATE base_interest_rate = 6.25;

INSERT INTO fd_products (product_name, min_amount, max_amount, min_term_days, max_term_days, base_interest_rate, is_active)
VALUES ('Golden Years Multiplier', 10000.00, 5000000.00, 366, 1825, 7.50, true)
ON DUPLICATE KEY UPDATE base_interest_rate = 7.50;
