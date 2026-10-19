ALTER TABLE fd_accounts
    MODIFY fa_principal_amount DECIMAL(24,8) NOT NULL,
    MODIFY fa_maturity_amount DECIMAL(24,8) NULL;
ALTER TABLE savings_accounts MODIFY sa_balance DECIMAL(24,8) NOT NULL DEFAULT 0;
ALTER TABLE savings_transactions
    MODIFY st_amount DECIMAL(24,8) NOT NULL,
    MODIFY st_balance_after DECIMAL(24,8) NOT NULL;
ALTER TABLE fd_interest_payout MODIFY fip_amount DECIMAL(24,8) NOT NULL;
ALTER TABLE fd_premature_closure
    MODIFY fpc_penalty_amount DECIMAL(24,8) NULL,
    MODIFY fpc_net_payable_amount DECIMAL(24,8) NULL;
ALTER TABLE fd_transactions
    MODIFY ft_amount DECIMAL(24,8) NOT NULL,
    MODIFY ft_balance_after DECIMAL(24,8) NULL;
ALTER TABLE fd_journals
    MODIFY fj_total_debit DECIMAL(24,8) NOT NULL,
    MODIFY fj_total_credit DECIMAL(24,8) NOT NULL,
    MODIFY fj_savings_balance_after DECIMAL(24,8) NULL;
ALTER TABLE fd_journal_lines
    MODIFY jl_debit DECIMAL(24,8) NOT NULL,
    MODIFY jl_credit DECIMAL(24,8) NOT NULL;
ALTER TABLE fd_product_currency_ref ADD COLUMN premature_penalty_points DECIMAL(9,4) NULL;

INSERT INTO transaction_types(tt_txn_type_code,tt_txn_type_name,tt_debit_credit) VALUES
('FD_INTEREST','Fixed deposit interest','CREDIT'),
('FD_MATURITY','Fixed deposit maturity','CREDIT'),
('FD_CLOSURE','Fixed deposit closure settlement','CREDIT'),
('FD_PENALTY','Fixed deposit premature closure penalty','DEBIT')
ON DUPLICATE KEY UPDATE tt_txn_type_name=VALUES(tt_txn_type_name);
