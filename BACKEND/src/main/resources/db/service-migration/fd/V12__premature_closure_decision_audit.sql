ALTER TABLE fd_premature_closure
    DROP CHECK ck_fd_premature_closure_status,
    ADD CONSTRAINT ck_fd_premature_closure_status
        CHECK (fpc_status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    ADD COLUMN fpc_decision_by BIGINT NULL,
    ADD COLUMN fpc_decision_at DATETIME(6) NULL,
    ADD COLUMN fpc_decision_reason VARCHAR(500) NULL;

CREATE INDEX idx_fpc_status_date ON fd_premature_closure (fpc_status, fpc_closure_date);
