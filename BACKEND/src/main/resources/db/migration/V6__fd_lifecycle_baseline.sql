DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.fd_accounts
        WHERE fa_principal_amount IS NULL OR fa_principal_amount <= 0
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: fd_accounts principal must be positive';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.fd_accounts
        WHERE fa_tenure_months IS NULL OR fa_tenure_months <= 0
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: fd_accounts tenure must be positive';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.fd_accounts
        WHERE fa_status IS NULL
           OR fa_status NOT IN ('PENDING', 'ACTIVE', 'MATURED', 'CLOSED', 'PREMATURELY_CLOSED')
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: fd_accounts status is outside the supported lifecycle vocabulary';
    END IF;

    IF EXISTS (
        SELECT fa_uuid
        FROM public.fd_accounts
        GROUP BY fa_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: duplicate fd_accounts.fa_uuid values';
    END IF;

    IF EXISTS (
        SELECT ft_uuid
        FROM public.fd_transactions
        GROUP BY ft_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: duplicate fd_transactions.ft_uuid values';
    END IF;

    IF EXISTS (
        SELECT fae_uuid
        FROM public.fd_account_events
        GROUP BY fae_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: duplicate fd_account_events.fae_uuid values';
    END IF;

    IF EXISTS (
        SELECT fip_uuid
        FROM public.fd_interest_payout
        GROUP BY fip_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: duplicate fd_interest_payout.fip_uuid values';
    END IF;

    IF EXISTS (
        SELECT fpc_uuid
        FROM public.fd_premature_closure
        GROUP BY fpc_uuid
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V6 FD invariant violation: duplicate fd_premature_closure.fpc_uuid values';
    END IF;
END $$;

ALTER TABLE public.fd_accounts
    ALTER COLUMN fa_status SET DEFAULT 'PENDING';

ALTER TABLE public.fd_accounts
    ADD CONSTRAINT ck_fd_accounts_status
        CHECK (fa_status IN ('PENDING', 'ACTIVE', 'MATURED', 'CLOSED', 'PREMATURELY_CLOSED)),
    ADD CONSTRAINT ck_fd_accounts_principal_positive
        CHECK (fa_principal_amount > 0),
    ADD CONSTRAINT ck_fd_accounts_tenure_positive
        CHECK (fa_tenure_months > 0),
    ADD COLUMN fa_row_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE public.fd_operation_idempotency (
    foi_id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    foi_operation_scope      VARCHAR(100) NOT NULL,
    foi_actor_key             VARCHAR(255) NOT NULL,
    foi_idempotency_key      VARCHAR(255) NOT NULL,
    foi_request_hash         VARCHAR(128) NOT NULL,
    foi_aggregate_id         BIGINT,
    foi_status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    foi_response_reference   VARCHAR(255),
    foi_created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    foi_completed_at         TIMESTAMPTZ,
    foi_uuid                 VARCHAR(64) NOT NULL DEFAULT gen_random_uuid()::text,
    CONSTRAINT ck_fd_operation_idempotency_scope_nonblank
        CHECK (length(btrim(foi_operation_scope)) > 0),
    CONSTRAINT ck_fd_operation_idempotency_actor_nonblank
        CHECK (length(btrim(foi_actor_key)) > 0),
    CONSTRAINT ck_fd_operation_idempotency_key_nonblank
        CHECK (length(btrim(foi_idempotency_key)) > 0),
    CONSTRAINT ck_fd_operation_idempotency_status
        CHECK (foi_status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT uq_fd_operation_idempotency_scope_key
        UNIQUE (foi_operation_scope, foi_actor_key, foi_idempotency_key),
    CONSTRAINT uq_fd_operation_idempotency_uuid UNIQUE (foi_uuid)
);

CREATE UNIQUE INDEX uq_fd_accounts_fa_uuid
    ON public.fd_accounts (fa_uuid);
CREATE UNIQUE INDEX uq_fd_transactions_ft_uuid
    ON public.fd_transactions (ft_uuid);
CREATE UNIQUE INDEX uq_fd_account_events_fae_uuid
    ON public.fd_account_events (fae_uuid);
CREATE UNIQUE INDEX uq_fd_interest_payout_fip_uuid
    ON public.fd_interest_payout (fip_uuid);
CREATE UNIQUE INDEX uq_fd_premature_closure_fpc_uuid
    ON public.fd_premature_closure (fpc_uuid);

CREATE INDEX idx_fd_operation_idempotency_aggregate_id
    ON public.fd_operation_idempotency (foi_aggregate_id);
CREATE INDEX idx_fd_accounts_status_maturity_date
    ON public.fd_accounts (fa_status, fa_maturity_date);
