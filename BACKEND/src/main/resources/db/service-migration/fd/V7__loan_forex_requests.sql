CREATE TABLE loan_applications (
    la_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    la_customer_id BIGINT NOT NULL,
    la_product_id BIGINT NOT NULL,
    la_requested_amount DECIMAL(18,4) NOT NULL,
    la_approved_amount DECIMAL(18,4),
    la_currency_id BIGINT NOT NULL,
    la_tenure_months INT NOT NULL,
    la_annual_rate DECIMAL(9,8) NOT NULL,
    la_purpose VARCHAR(500) NOT NULL,
    la_destination_savings_id BIGINT NOT NULL,
    la_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    la_decision_admin_id BIGINT,
    la_decided_at DATETIME(6),
    la_rejection_reason VARCHAR(500),
    la_disbursement_transaction_id BIGINT,
    la_idempotency_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    la_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    la_updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_loan_submit_key UNIQUE (la_customer_id, la_idempotency_key),
    CONSTRAINT uq_loan_disbursement UNIQUE (la_disbursement_transaction_id),
    CONSTRAINT ck_loan_amount CHECK (la_requested_amount > 0 AND (la_approved_amount IS NULL OR la_approved_amount > 0)),
    CONSTRAINT ck_loan_tenure CHECK (la_tenure_months > 0),
    CONSTRAINT ck_loan_status CHECK (la_status IN ('PENDING_REVIEW','APPROVED','REJECTED','DISBURSED','ACTIVE','CLOSED')),
    CONSTRAINT fk_loan_customer FOREIGN KEY (la_customer_id) REFERENCES fd_user_ref(fur_user_id),
    CONSTRAINT fk_loan_currency FOREIGN KEY (la_currency_id) REFERENCES fd_currency_ref(fcr_currency_id),
    CONSTRAINT fk_loan_destination FOREIGN KEY (la_destination_savings_id) REFERENCES savings_accounts(sa_account_id),
    CONSTRAINT fk_loan_transaction FOREIGN KEY (la_disbursement_transaction_id) REFERENCES savings_transactions(st_id),
    INDEX idx_loan_customer (la_customer_id,la_created_at),
    INDEX idx_loan_status (la_status,la_created_at)
);

CREATE TABLE loan_application_events (
    le_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    le_application_id BIGINT NOT NULL,
    le_status VARCHAR(20) NOT NULL,
    le_actor_id BIGINT NOT NULL,
    le_reason VARCHAR(500),
    le_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_loan_event_application FOREIGN KEY (le_application_id) REFERENCES loan_applications(la_id),
    INDEX idx_loan_event (le_application_id,le_id)
);

CREATE TABLE forex_requests (
    fr_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fr_customer_id BIGINT NOT NULL,
    fr_product_id BIGINT NOT NULL,
    fr_funding_currency_id BIGINT NOT NULL,
    fr_destination_currency_id BIGINT NOT NULL,
    fr_amount DECIMAL(18,4) NOT NULL,
    fr_markup_rate DECIMAL(9,8) NOT NULL,
    fr_fee DECIMAL(18,4) NOT NULL DEFAULT 0,
    fr_status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    fr_idempotency_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fr_created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_forex_submit_key UNIQUE (fr_customer_id,fr_idempotency_key),
    CONSTRAINT ck_forex_amount CHECK (fr_amount > 0 AND fr_fee >= 0),
    CONSTRAINT ck_forex_status CHECK (fr_status IN ('REQUESTED','CANCELLED')),
    CONSTRAINT ck_forex_currencies CHECK (fr_funding_currency_id <> fr_destination_currency_id),
    CONSTRAINT fk_forex_customer FOREIGN KEY (fr_customer_id) REFERENCES fd_user_ref(fur_user_id),
    CONSTRAINT fk_forex_funding_currency FOREIGN KEY (fr_funding_currency_id) REFERENCES fd_currency_ref(fcr_currency_id),
    CONSTRAINT fk_forex_destination_currency FOREIGN KEY (fr_destination_currency_id) REFERENCES fd_currency_ref(fcr_currency_id),
    INDEX idx_forex_customer (fr_customer_id,fr_created_at)
);

ALTER TABLE fd_transactions ADD COLUMN ft_source_savings_account_id BIGINT NULL;
ALTER TABLE fd_transactions ADD CONSTRAINT fk_fd_transaction_source_savings FOREIGN KEY (ft_source_savings_account_id) REFERENCES savings_accounts(sa_account_id);
