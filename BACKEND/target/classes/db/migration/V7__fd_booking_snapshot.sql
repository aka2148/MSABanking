DO $$
BEGIN
    IF EXISTS (
        SELECT fur_user_uuid
        FROM public.fd_user_ref
        GROUP BY fur_user_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V7 FD invariant violation: duplicate fd_user_ref.fur_user_uuid values';
    END IF;
END $$;

ALTER TABLE public.fd_accounts
    ADD COLUMN fa_policy_version VARCHAR(50) NOT NULL DEFAULT 'academic-v1';

ALTER TABLE public.fd_accounts
    ADD CONSTRAINT ck_fd_accounts_policy_version_nonblank
        CHECK (length(btrim(fa_policy_version)) > 0);

CREATE UNIQUE INDEX uq_fd_user_ref_user_uuid
    ON public.fd_user_ref (fur_user_uuid);

INSERT INTO public.transaction_types
    (tt_txn_type_code, tt_txn_type_name, tt_debit_credit, tt_requires_approval)
VALUES
    ('DEPOSIT', 'Internal FD funding record', 'CREDIT', FALSE)
ON CONFLICT (tt_txn_type_code) DO NOTHING;
