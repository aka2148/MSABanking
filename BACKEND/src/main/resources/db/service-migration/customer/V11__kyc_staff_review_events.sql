ALTER TABLE customer_kyc_events DROP CHECK chk_kyc_event;

ALTER TABLE customer_kyc_events
    ADD CONSTRAINT chk_kyc_event CHECK (
        event_type IN ('AUTO_VERIFIED','REKYC_REQUESTED','KYC_SUBMITTED','STAFF_VERIFIED','STAFF_REJECTED')
    );
