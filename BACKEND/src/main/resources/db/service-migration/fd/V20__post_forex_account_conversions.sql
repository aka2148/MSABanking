-- Add own-account Forex posting terms and preserve the loan ledger operation
-- introduced in V18. Each schema change is conditional so a database that ran
-- the earlier Forex V18/V19 migrations can be safely reconciled after Flyway
-- history is repaired to the canonical V18 loan / V19 precision / V20 Forex order.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND column_name = 'fr_source_savings_account_id') = 0,
    'ALTER TABLE forex_requests ADD COLUMN fr_source_savings_account_id BIGINT NULL', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND column_name = 'fr_destination_savings_account_id') = 0,
    'ALTER TABLE forex_requests ADD COLUMN fr_destination_savings_account_id BIGINT NULL', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND column_name = 'fr_reference_rate') = 0,
    'ALTER TABLE forex_requests ADD COLUMN fr_reference_rate DECIMAL(22,12) NULL', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND column_name = 'fr_customer_rate') = 0,
    'ALTER TABLE forex_requests ADD COLUMN fr_customer_rate DECIMAL(22,12) NULL', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND column_name = 'fr_destination_amount') = 0,
    'ALTER TABLE forex_requests ADD COLUMN fr_destination_amount DECIMAL(22,8) NULL', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND column_name = 'fr_total_debit') = 0,
    'ALTER TABLE forex_requests ADD COLUMN fr_total_debit DECIMAL(22,8) NULL', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

-- Replace these checks even when an earlier V20 attempt stopped part-way through.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'forex_requests'
      AND constraint_name = 'ck_forex_status' AND constraint_type = 'CHECK') > 0,
    'ALTER TABLE forex_requests DROP CHECK ck_forex_status', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

ALTER TABLE forex_requests
    ADD CONSTRAINT ck_forex_status CHECK (fr_status IN ('REQUESTED','CANCELLED','COMPLETED'));

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'forex_requests'
      AND constraint_name = 'ck_forex_completed_terms' AND constraint_type = 'CHECK') > 0,
    'ALTER TABLE forex_requests DROP CHECK ck_forex_completed_terms', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

ALTER TABLE forex_requests
    ADD CONSTRAINT ck_forex_completed_terms CHECK (
        fr_status <> 'COMPLETED' OR (
            fr_source_savings_account_id IS NOT NULL
            AND fr_destination_savings_account_id IS NOT NULL
            AND fr_reference_rate > 0
            AND fr_customer_rate > 0
            AND fr_destination_amount > 0
            AND fr_total_debit > 0
        )
    );

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'forex_requests'
      AND constraint_name = 'fk_forex_source_account' AND constraint_type = 'FOREIGN KEY') = 0,
    'ALTER TABLE forex_requests ADD CONSTRAINT fk_forex_source_account FOREIGN KEY (fr_source_savings_account_id, fr_customer_id, fr_funding_currency_id) REFERENCES savings_accounts (sa_account_id, sa_holder_id, sa_currency_id)', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'forex_requests'
      AND constraint_name = 'fk_forex_destination_account' AND constraint_type = 'FOREIGN KEY') = 0,
    'ALTER TABLE forex_requests ADD CONSTRAINT fk_forex_destination_account FOREIGN KEY (fr_destination_savings_account_id, fr_customer_id, fr_destination_currency_id) REFERENCES savings_accounts (sa_account_id, sa_holder_id, sa_currency_id)', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND index_name = 'idx_forex_source_account') = 0,
    'ALTER TABLE forex_requests ADD INDEX idx_forex_source_account (fr_source_savings_account_id)', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'forex_requests'
      AND index_name = 'idx_forex_destination_account') = 0,
    'ALTER TABLE forex_requests ADD INDEX idx_forex_destination_account (fr_destination_savings_account_id)', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

-- V18 seeds the loan account and permits loan postings. Keep those additions
-- alongside the Forex accounts and operation after both branch histories merge.
INSERT IGNORE INTO chart_of_accounts(coa_code, coa_name, coa_type, coa_active) VALUES
    ('LOAN_RECEIVABLE', 'Loan principal receivable', 'ASSET', TRUE),
    ('FOREX_CLEARING', 'Forex conversion clearing', 'LIABILITY', TRUE),
    ('FOREX_FEE_INCOME', 'Forex service fee income', 'INCOME', TRUE);

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'fd_journals'
      AND constraint_name = 'fd_journals_fj_operation_check' AND constraint_type = 'CHECK') > 0,
    'ALTER TABLE fd_journals DROP CHECK fd_journals_fj_operation_check', 'SELECT 1');
PREPARE forex_migration_stmt FROM @ddl;
EXECUTE forex_migration_stmt;
DEALLOCATE PREPARE forex_migration_stmt;

ALTER TABLE fd_journals ADD CONSTRAINT fd_journals_fj_operation_check
    CHECK (fj_operation IN ('FUNDING','INTEREST','SETTLEMENT','DEMO_OPENING','TELLER_DEPOSIT','TELLER_WITHDRAWAL','TRANSFER','WITHDRAWAL','LOAN_DISBURSEMENT','FOREX'));
