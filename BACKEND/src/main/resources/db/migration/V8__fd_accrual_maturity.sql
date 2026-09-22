DO $$
BEGIN
    IF EXISTS (
        SELECT fia_uuid
        FROM public.fd_interest_accrual
        GROUP BY fia_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V8 FD invariant violation: duplicate fd_interest_accrual.fia_uuid values';
    END IF;
END $$;

INSERT INTO public.transaction_types
    (tt_txn_type_code, tt_txn_type_name, tt_debit_credit, tt_requires_approval)
VALUES
    ('INTEREST_ACCRUAL', 'Internal FD interest accrual record', 'CREDIT', FALSE),
    ('MATURITY', 'Internal FD maturity record', 'CREDIT', FALSE)
ON CONFLICT (tt_txn_type_code) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fd_interest_accrual_account_date
    ON public.fd_interest_accrual (fia_account_id, fia_accrual_date);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fd_interest_accrual_fia_uuid
    ON public.fd_interest_accrual (fia_uuid);
