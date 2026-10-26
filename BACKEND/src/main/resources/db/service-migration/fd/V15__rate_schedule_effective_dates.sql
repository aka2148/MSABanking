-- V15: Add effective-date support to replicated FD product rate schedules
-- Allows rate schedules to specify effective start and end dates.
-- Existing rows default to NULL (treated as open-ended / always effective) ensuring full backward compatibility.

ALTER TABLE fd_product_rate_ref
    ADD COLUMN rate_ref_id BIGINT NOT NULL AUTO_INCREMENT FIRST,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (rate_ref_id),
    ADD COLUMN effective_from DATE NULL,
    ADD COLUMN effective_to DATE NULL,
    ADD INDEX idx_fd_product_rate_lookup (product_id, currency_id, minimum_tenure_months, maximum_tenure_months);
