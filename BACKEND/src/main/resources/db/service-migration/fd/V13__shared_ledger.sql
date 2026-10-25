CREATE TABLE chart_of_accounts (
    coa_code VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs PRIMARY KEY,
    coa_name VARCHAR(120) NOT NULL,
    coa_type VARCHAR(20) NOT NULL CHECK (coa_type IN ('ASSET','LIABILITY','EQUITY','INCOME','EXPENSE')),
    coa_active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

INSERT IGNORE INTO chart_of_accounts(coa_code, coa_name, coa_type, coa_active) VALUES
    ('SAVINGS', 'Savings customer liability', 'LIABILITY', TRUE),
    ('FIXED_DEPOSIT', 'Fixed deposit liability', 'LIABILITY', TRUE),
    ('INTEREST_EXPENSE', 'Interest expense', 'EXPENSE', TRUE),
    ('CASH_TELLER', 'Teller cash', 'ASSET', TRUE),
    ('DEMO_SUSPENSE', 'Demonstration opening equity', 'EQUITY', TRUE);

ALTER TABLE fd_journals
    DROP CHECK fd_journals_fj_operation_check,
    ADD CONSTRAINT fd_journals_fj_operation_check
        CHECK (fj_operation IN ('FUNDING','INTEREST','SETTLEMENT','DEMO_OPENING','TELLER_DEPOSIT','TELLER_WITHDRAWAL','TRANSFER')),
    ADD COLUMN fj_created_by VARCHAR(255) NULL;

ALTER TABLE fd_journal_lines
    ADD COLUMN jl_savings_account_id BIGINT NULL,
    ADD INDEX idx_fd_journal_lines_savings_account (jl_savings_account_id),
    ADD CONSTRAINT fd_journal_lines_savings_account_fk FOREIGN KEY (jl_savings_account_id)
        REFERENCES savings_accounts(sa_account_id);

CREATE TABLE fd_ledger_cutover (
    flc_singleton_id TINYINT NOT NULL PRIMARY KEY CHECK (flc_singleton_id = 1),
    flc_cutover_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;

INSERT INTO fd_ledger_cutover(flc_singleton_id, flc_cutover_at) VALUES (1, UTC_TIMESTAMP(6));
