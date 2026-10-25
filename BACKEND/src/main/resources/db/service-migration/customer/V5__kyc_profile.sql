CREATE TABLE IF NOT EXISTS customer_kyc_profile (
    customer_id BIGINT NOT NULL PRIMARY KEY,
    cycle_number INT NOT NULL DEFAULT 1,
    cycle_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    date_of_birth DATE NULL,
    gender VARCHAR(16) NULL,
    relationship VARCHAR(16) NULL,
    father_or_spouse_name VARCHAR(100) NULL,
    occupation VARCHAR(32) NULL,
    country_of_origin CHAR(2) NULL,
    proof_of_id_type VARCHAR(32) NULL,
    proof_of_id_value VARCHAR(20) NULL,
    id_issue_date DATE NULL,
    confirmed_fields VARCHAR(255) NOT NULL DEFAULT '',
    proof_number_masked VARCHAR(24) NULL,
    document_name VARCHAR(255) NULL,
    document_content_type VARCHAR(64) NULL,
    document_size BIGINT NULL,
    document_bytes MEDIUMBLOB NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_kyc_customer FOREIGN KEY (customer_id) REFERENCES customers(cus_customer_id),
    CONSTRAINT chk_kyc_cycle CHECK (cycle_number >= 1 AND cycle_status IN ('OPEN','COMPLETED'))
);

CREATE TABLE IF NOT EXISTS customer_kyc_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    cycle_number INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    remarks VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_kyc_event_customer FOREIGN KEY (customer_id) REFERENCES customers(cus_customer_id),
    CONSTRAINT uq_kyc_cycle_event UNIQUE (customer_id, cycle_number, event_type),
    CONSTRAINT chk_kyc_event CHECK (event_type IN ('AUTO_VERIFIED','REKYC_REQUESTED'))
);
