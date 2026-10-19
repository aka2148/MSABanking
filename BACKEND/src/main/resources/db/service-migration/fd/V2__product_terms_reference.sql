CREATE TABLE fd_product_currency_ref (
    product_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL,
    minimum_amount DECIMAL(18,4) NOT NULL,
    maximum_amount DECIMAL(18,4) NOT NULL,
    is_base_currency TINYINT(1) NOT NULL DEFAULT 0,
    source_version BIGINT NOT NULL,
    PRIMARY KEY (product_id, currency_id),
    CONSTRAINT fk_fd_product_currency_product FOREIGN KEY (product_id) REFERENCES fd_product_ref(fpr_product_id),
    CONSTRAINT fk_fd_product_currency_currency FOREIGN KEY (currency_id) REFERENCES fd_currency_ref(fcr_currency_id),
    CONSTRAINT ck_fd_product_currency_amounts CHECK (minimum_amount > 0 AND maximum_amount >= minimum_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

CREATE TABLE fd_product_rate_ref (
    product_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL,
    minimum_tenure_months INT NOT NULL,
    maximum_tenure_months INT NOT NULL,
    annual_rate DECIMAL(9,4) NOT NULL,
    compounding_basis VARCHAR(30),
    payout_frequency VARCHAR(30),
    source_version BIGINT NOT NULL,
    PRIMARY KEY (product_id, currency_id, minimum_tenure_months, maximum_tenure_months),
    CONSTRAINT fk_fd_product_rate_product FOREIGN KEY (product_id) REFERENCES fd_product_ref(fpr_product_id),
    CONSTRAINT fk_fd_product_rate_currency FOREIGN KEY (currency_id) REFERENCES fd_currency_ref(fcr_currency_id),
    CONSTRAINT ck_fd_product_rate_tenure CHECK (minimum_tenure_months > 0 AND maximum_tenure_months >= minimum_tenure_months),
    CONSTRAINT ck_fd_product_rate_nonnegative CHECK (annual_rate >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

INSERT INTO transaction_types(tt_txn_type_code,tt_txn_type_name,tt_debit_credit) VALUES
('FD_FUNDING','Fixed deposit funding','DEBIT'),
('FD_SETTLEMENT','Fixed deposit settlement','CREDIT')
ON DUPLICATE KEY UPDATE tt_txn_type_name=VALUES(tt_txn_type_name);
