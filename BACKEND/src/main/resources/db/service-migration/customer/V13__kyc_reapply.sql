ALTER TABLE customer_kyc_events DROP CHECK chk_kyc_event;
ALTER TABLE customer_kyc_events
    ADD CONSTRAINT chk_kyc_event CHECK (
        event_type IN ('AUTO_VERIFIED','REKYC_REQUESTED','KYC_SUBMITTED','STAFF_VERIFIED','STAFF_REJECTED','KYC_REAPPLIED')
    );

CREATE TABLE customer_mpin_proof_use (
    jti VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    consumed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_customer_mpin_proof_user FOREIGN KEY (user_id) REFERENCES users(usr_user_id)
);
