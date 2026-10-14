-- Minimal savings integration. No balances are seeded and no existing FD is debited.
CREATE TABLE public.savings_accounts (
    sa_account_id BIGSERIAL PRIMARY KEY,
    sa_account_number VARCHAR(50) NOT NULL UNIQUE,
    sa_holder_id BIGINT NOT NULL REFERENCES public.fd_user_ref(fur_user_id),
    sa_currency_id BIGINT NOT NULL REFERENCES public.fd_currency_ref(fcr_currency_id),
    sa_display_name VARCHAR(80) NOT NULL,
    sa_balance NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK (sa_balance >= 0),
    sa_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (sa_status IN ('ACTIVE', 'INACTIVE')),
    sa_created_date TIMESTAMPTZ NOT NULL,
    sa_row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_savings_holder_currency UNIQUE (sa_account_id, sa_holder_id, sa_currency_id)
);
CREATE INDEX idx_savings_holder ON public.savings_accounts(sa_holder_id);
-- Nullable for historical FDs. Their original source must not be guessed.
ALTER TABLE public.fd_accounts ADD COLUMN fa_funding_savings_account_id BIGINT;
ALTER TABLE public.fd_accounts ADD CONSTRAINT fk_fd_funding_savings
    FOREIGN KEY (fa_funding_savings_account_id, fa_primary_holder_id, fa_currency_id)
    REFERENCES public.savings_accounts(sa_account_id, sa_holder_id, sa_currency_id);
CREATE INDEX idx_fd_funding_savings ON public.fd_accounts(fa_funding_savings_account_id);
