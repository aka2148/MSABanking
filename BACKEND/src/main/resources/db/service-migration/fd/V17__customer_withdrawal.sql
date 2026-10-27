ALTER TABLE fd_journals DROP CHECK fd_journals_fj_operation_check;
ALTER TABLE fd_journals ADD CONSTRAINT fd_journals_fj_operation_check CHECK (fj_operation IN ('FUNDING','INTEREST','SETTLEMENT','DEMO_OPENING','TELLER_DEPOSIT','TELLER_WITHDRAWAL','TRANSFER','WITHDRAWAL'));
INSERT IGNORE INTO chart_of_accounts(coa_code,coa_name,coa_type,coa_active) VALUES ('WITHDRAWAL_CLEARING','Simulated withdrawal clearing','ASSET',TRUE);
