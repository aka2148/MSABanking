ALTER TABLE savings_accounts
    ADD COLUMN sa_account_type VARCHAR(20) NOT NULL DEFAULT 'SAVINGS',
    ADD COLUMN sa_product_id BIGINT NULL,
    ADD COLUMN sa_product_code VARCHAR(80) NULL,
    ADD COLUMN sa_product_name VARCHAR(255) NULL,
    ADD COLUMN sa_minimum_balance DECIMAL(18,4) NOT NULL DEFAULT 0,
    ADD COLUMN sa_interest_terms JSON NULL,
    ADD COLUMN sa_interest_start_date DATE NULL,
    DROP CHECK savings_accounts_sa_status_check,
    ADD CONSTRAINT savings_accounts_sa_status_check CHECK (sa_status IN ('ACTIVE','INACTIVE','CLOSED')),
    ADD CONSTRAINT savings_accounts_sa_account_type_check CHECK (sa_account_type IN ('SAVINGS','CURRENT')),
    ADD CONSTRAINT savings_accounts_sa_minimum_balance_check CHECK (sa_minimum_balance >= 0),
    ADD INDEX idx_savings_holder_type (sa_holder_id,sa_account_type);

CREATE TABLE savings_account_status_audit (
    sasa_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sasa_account_id BIGINT NOT NULL,
    sasa_from_status VARCHAR(20) NULL,
    sasa_to_status VARCHAR(20) NOT NULL,
    sasa_reason VARCHAR(140) NOT NULL,
    sasa_actor_username VARCHAR(255) NOT NULL,
    sasa_created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_sasa_account FOREIGN KEY (sasa_account_id) REFERENCES savings_accounts(sa_account_id),
    INDEX idx_sasa_account_created (sasa_account_id,sasa_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
