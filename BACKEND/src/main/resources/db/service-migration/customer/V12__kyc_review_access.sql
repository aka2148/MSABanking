CREATE TABLE IF NOT EXISTS customer_kyc_access_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    cycle_number INT NOT NULL,
    staff_username VARCHAR(255) NOT NULL,
    action VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_kyc_access_action CHECK (action IN ('VIEW_DETAIL','VIEW_DOCUMENT','DECISION')),
    INDEX idx_kyc_access_customer (customer_id, cycle_number, created_at)
);
ALTER TABLE customer_kyc_events ADD COLUMN reviewer_username VARCHAR(255) NULL;
-- remarks already permits the full 500-character decision reason.
