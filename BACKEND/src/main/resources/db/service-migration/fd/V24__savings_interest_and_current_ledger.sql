CREATE TABLE savings_interest_accrual (
    sia_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sia_account_id BIGINT NOT NULL,
    sia_business_date DATE NOT NULL,
    sia_balance_basis DECIMAL(24,8) NOT NULL,
    sia_rate DECIMAL(18,12) NOT NULL,
    sia_interest DECIMAL(24,8) NOT NULL,
    sia_credited_reference VARCHAR(80) NULL,
    sia_created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_sia_account FOREIGN KEY (sia_account_id) REFERENCES savings_accounts(sa_account_id),
    CONSTRAINT uq_sia_account_date UNIQUE (sia_account_id,sia_business_date),
    INDEX idx_sia_uncredited (sia_account_id,sia_credited_reference,sia_business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

INSERT IGNORE INTO chart_of_accounts(coa_code,coa_name,coa_type,coa_active)
VALUES ('CURRENT_ACCOUNT','Current account customer liability','LIABILITY',TRUE);

ALTER TABLE fd_journals
    DROP CHECK fd_journals_fj_operation_check,
    ADD CONSTRAINT fd_journals_fj_operation_check
        CHECK (fj_operation IN ('FUNDING','INTEREST','SETTLEMENT','DEMO_OPENING','TELLER_DEPOSIT','TELLER_WITHDRAWAL','TRANSFER','WITHDRAWAL','LOAN_DISBURSEMENT','FOREX','SAVINGS_INTEREST'));
