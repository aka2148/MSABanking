CREATE TABLE IF NOT EXISTS fd_staff_withdrawal_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    savings_account_id BIGINT NOT NULL,
    customer_user_id BIGINT NOT NULL,
    staff_username VARCHAR(255) NOT NULL,
    amount DECIMAL(24,8) NOT NULL,
    currency_id BIGINT NOT NULL,
    reference VARCHAR(100) NOT NULL UNIQUE,
    verification_method VARCHAR(30) NOT NULL,
    verification_reference VARCHAR(40),
    remarks VARCHAR(140) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_withdrawal_verification CHECK (verification_method IN
        ('ID_DOCUMENT_CHECKED','SIGNATURE_VERIFIED','BIOMETRIC_VERIFIED','OTHER')),
    CONSTRAINT chk_withdrawal_amount CHECK (amount > 0),
    INDEX idx_withdrawal_account (savings_account_id, created_at)
);
