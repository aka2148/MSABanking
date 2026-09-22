DO $$
BEGIN
    IF EXISTS (
        SELECT fpc_uuid
        FROM public.fd_premature_closure
        GROUP BY fpc_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V9 FD invariant violation: duplicate fd_premature_closure.fpc_uuid values';
    END IF;
END $$;

INSERT INTO public.transaction_types
    (tt_txn_type_code, tt_txn_type_name, tt_debit_credit, tt_requires_approval)
VALUES
    ('CLOSURE', 'Internal FD closure settlement record', 'CREDIT', FALSE),
    ('PENALTY', 'Internal FD premature-closure penalty record', 'CREDIT', FALSE)
ON CONFLICT (tt_txn_type_code) DO NOTHING;

ALTER TABLE public.fd_premature_closure
    ADD COLUMN IF NOT EXISTS fpc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_fd_premature_closure_status'
    ) THEN
        ALTER TABLE public.fd_premature_closure
            ADD CONSTRAINT ck_fd_premature_closure_status
                CHECK (fpc_status IN ('PENDING', 'APPROVED'));
    END IF;
END $$;
