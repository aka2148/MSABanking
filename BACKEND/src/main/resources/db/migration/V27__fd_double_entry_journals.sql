CREATE TABLE public.fd_journals (
    fj_id BIGSERIAL PRIMARY KEY,
    fj_key VARCHAR(100) NOT NULL UNIQUE,
    fj_fd_account_id BIGINT REFERENCES public.fd_accounts(fa_account_id),
    fj_savings_account_id BIGINT NOT NULL REFERENCES public.savings_accounts(sa_account_id),
    fj_currency_id BIGINT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    fj_operation VARCHAR(30) NOT NULL CHECK (fj_operation IN ('FUNDING','INTEREST','SETTLEMENT','DEMO_OPENING')),
    fj_posted_at TIMESTAMPTZ NOT NULL,
    fj_total_debit NUMERIC(18,4) NOT NULL CHECK (fj_total_debit > 0),
    fj_total_credit NUMERIC(18,4) NOT NULL,
    fj_savings_balance_after NUMERIC(18,4),
    CONSTRAINT ck_fd_journal_balanced CHECK (fj_total_debit = fj_total_credit),
    CONSTRAINT uq_fd_journal_operation UNIQUE (fj_fd_account_id, fj_operation)
);
CREATE TABLE public.fd_journal_lines (
    jl_journal_id BIGINT NOT NULL REFERENCES public.fd_journals(fj_id),
    jl_sequence INTEGER NOT NULL,
    jl_account_code VARCHAR(50) NOT NULL,
    jl_debit NUMERIC(18,4) NOT NULL,
    jl_credit NUMERIC(18,4) NOT NULL,
    PRIMARY KEY (jl_journal_id,jl_sequence),
    CONSTRAINT ck_fd_journal_line_side CHECK ((jl_debit > 0 AND jl_credit = 0) OR (jl_credit > 0 AND jl_debit = 0))
);
